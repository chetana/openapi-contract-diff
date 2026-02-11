package io.github.chetana.openapi.diff;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.*;
import org.openapitools.openapidiff.core.output.ConsoleRender;
import org.openapitools.openapidiff.core.output.Render;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpenApiDiffService {

    public record MetadataChange(String path, String method, String field, String designFirstValue, String generatedValue) {}
    public record StructureChange(String method, String path, String changeType, List<String> details, boolean isBreaking) {}
    public record DiffResult(String consoleReport, List<MetadataChange> metadataChanges, List<StructureChange> structureChanges, boolean isDifferent, List<String> missingOperationIds) {}

    public InputStream exportToCsv(DiffResult result) {
        StringBuilder csv = new StringBuilder();
        // Header
        csv.append("Category,Path,Method,Element,Contract Design First (Reference),Generated Contract,Is Breaking\n");

        // Missing Operations (those that couldn't be found at all)
        for (String missingOp : result.missingOperationIds()) {
            csv.append("Missing Operation,,,")
                    .append(escapeCsv(missingOp)).append(",")
                    .append("PRESENT,")
                    .append("ABSENT,")
                    .append("TRUE")
                    .append("\n");
        }

        // Structure Changes
        for (StructureChange change : result.structureChanges()) {
            String designVal = "";
            String genVal = "";
            
            if ("REMOVED".equals(change.changeType())) {
                designVal = "PRESENT";
                genVal = "ABSENT";
            } else if ("NEW".equals(change.changeType())) {
                designVal = "ABSENT";
                genVal = "PRESENT";
            } else {
                designVal = "MATCH (Structural change)";
                genVal = String.join("; ", change.details());
            }

            csv.append("Structure,")
                    .append(escapeCsv(change.path())).append(",")
                    .append(escapeCsv(change.method())).append(",")
                    .append(escapeCsv(change.changeType())).append(",")
                    .append(escapeCsv(designVal)).append(",")
                    .append(escapeCsv(genVal)).append(",")
                    .append(change.isBreaking())
                    .append("\n");
        }

        // Metadata Changes
        for (MetadataChange change : result.metadataChanges()) {
            csv.append("Metadata,")
                    .append(escapeCsv(change.path())).append(",")
                    .append(escapeCsv(change.method())).append(",")
                    .append(escapeCsv(change.field())).append(",")
                    .append(escapeCsv(change.designFirstValue())).append(",")
                    .append(escapeCsv(change.generatedValue())).append(",")
                    .append("FALSE")
                    .append("\n");
        }

        return new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public DiffResult compare(String pmSpecContent, String generatedSpecInput) throws Exception {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);

        OpenAPI pmOpenAPI = new OpenAPIV3Parser().readContents(pmSpecContent, null, options).getOpenAPI();
        
        OpenAPI genOpenAPI;
        if (generatedSpecInput.trim().startsWith("http")) {
            genOpenAPI = new OpenAPIV3Parser().read(generatedSpecInput.trim(), null, options);
        } else {
            genOpenAPI = new OpenAPIV3Parser().readContents(generatedSpecInput, null, options).getOpenAPI();
        }

        if (pmOpenAPI == null || genOpenAPI == null) {
            throw new IllegalArgumentException("Could not parse one of the OpenAPI specifications. Ensure the content is valid JSON/YAML or the URL is accessible.");
        }

        normalizeAllDescriptions(pmOpenAPI);
        normalizeAllDescriptions(genOpenAPI);

        List<String> missingOperationIds = new ArrayList<>();
        OpenAPI filteredGenOpenAPI = filterGeneratedOpenApi(pmOpenAPI, genOpenAPI, missingOperationIds);

        String pmJson = Json.pretty(pmOpenAPI);
        String genJson = Json.pretty(filteredGenOpenAPI);

        ChangedOpenApi diff = OpenApiCompare.fromContents(pmJson, genJson);

        String consoleReport = renderToString(new ConsoleRender(), diff);
        List<MetadataChange> metadataChanges = extractMetadataChanges(diff);
        List<StructureChange> structureChanges = extractStructureChanges(diff);

        return new DiffResult(consoleReport, metadataChanges, structureChanges, diff.isDifferent(), missingOperationIds);
    }

    private OpenAPI filterGeneratedOpenApi(OpenAPI pmOpenAPI, OpenAPI genOpenAPI, List<String> missingOperationIds) {
        OpenAPI filteredGenOpenAPI = new OpenAPI();
        filteredGenOpenAPI.setOpenapi(genOpenAPI.getOpenapi());
        filteredGenOpenAPI.setInfo(genOpenAPI.getInfo());
        filteredGenOpenAPI.setComponents(genOpenAPI.getComponents());
        io.swagger.v3.oas.models.Paths newPaths = new io.swagger.v3.oas.models.Paths();

        pmOpenAPI.getPaths().forEach((path, pmPathItem) -> {
            PathItem genPathItem = new PathItem();
            pmPathItem.readOperationsMap().forEach((method, pmOp) -> {
                String opId = pmOp.getOperationId();
                Operation foundGenOp = findOperation(genOpenAPI, path, method, opId);
                if (foundGenOp != null) {
                    setOperationByMethod(genPathItem, method, foundGenOp);
                } else {
                    missingOperationIds.add(opId != null ? opId : method + " " + path);
                }
            });
            if (!genPathItem.readOperationsMap().isEmpty()) {
                newPaths.addPathItem(path, genPathItem);
            }
        });
        filteredGenOpenAPI.setPaths(newPaths);
        return filteredGenOpenAPI;
    }

    private List<StructureChange> extractStructureChanges(ChangedOpenApi diff) {
        List<StructureChange> changes = new ArrayList<>();

        // New Endpoints
        diff.getNewEndpoints().forEach(endpoint -> {
            changes.add(new StructureChange(endpoint.getMethod().toString(), endpoint.getPathUrl(), "NEW", 
                List.of("Endpoint added in generated contract"), false));
        });

        // Missing Endpoints
        diff.getMissingEndpoints().forEach(endpoint -> {
            changes.add(new StructureChange(endpoint.getMethod().toString(), endpoint.getPathUrl(), "REMOVED", 
                List.of("Endpoint missing from generated contract"), true));
        });

        // Changed Operations
        diff.getChangedOperations().forEach(op -> {
            List<String> details = new ArrayList<>();
            
            if (op.getParameters() != null && op.getParameters().isDifferent()) {
                op.getParameters().getIncreased().forEach(p -> details.add("Added parameter: " + p.getName()));
                op.getParameters().getMissing().forEach(p -> details.add("Removed parameter: " + p.getName()));
                op.getParameters().getChanged().forEach(p -> details.add("Changed parameter: " + p.getName()));
            }

            if (op.getApiResponses() != null && op.getApiResponses().isDifferent()) {
                op.getApiResponses().getIncreased().forEach((code, resp) -> details.add("Added response: " + code));
                op.getApiResponses().getMissing().forEach((code, resp) -> details.add("Removed response: " + code));
                op.getApiResponses().getChanged().forEach((code, resp) -> {
                    details.add("Changed response: " + code);
                    if (resp.getContent() != null && resp.getContent().isDifferent()) {
                        resp.getContent().getChanged().forEach((mediaType, change) -> {
                            details.add("  Media Type: " + mediaType);
                            if (change.getSchema() != null && change.getSchema().isDifferent()) {
                                extractSchemaDetails(change.getSchema(), "    ", details);
                            }
                        });
                    }
                });
            }

            if (!details.isEmpty() || !op.isCompatible()) {
                changes.add(new StructureChange(op.getHttpMethod().toString(), op.getPathUrl(), "CHANGED", 
                    details.isEmpty() ? List.of("Structural changes detected") : details, !op.isCompatible()));
            }
        });

        return changes;
    }

    private void extractSchemaDetails(ChangedSchema schema, String indent, List<String> details) {
        if (schema.getMissingProperties() != null) {
            schema.getMissingProperties().forEach((name, s) -> details.add(indent + "Missing property: " + name));
        }
        if (schema.getIncreasedProperties() != null) {
            schema.getIncreasedProperties().forEach((name, s) -> details.add(indent + "New property: " + name));
        }
        if (schema.getChangedProperties() != null) {
            schema.getChangedProperties().forEach((name, s) -> {
                details.add(indent + "Changed property: " + name);
                extractSchemaDetails(s, indent + "  ", details);
            });
        }
        if (schema.getItems() != null && schema.getItems().isDifferent()) {
            details.add(indent + "Items changed:");
            extractSchemaDetails(schema.getItems(), indent + "  ", details);
        }
    }

    private List<MetadataChange> extractMetadataChanges(ChangedOpenApi diff) {
        List<MetadataChange> changes = new ArrayList<>();
        diff.getChangedOperations().forEach(op -> {
            String path = op.getPathUrl();
            String method = op.getHttpMethod().toString();

            if (op.getSummary() != null && op.getSummary().isDifferent()) {
                changes.add(new MetadataChange(path, method, "Summary", 
                        String.valueOf(op.getSummary().getLeft()), 
                        String.valueOf(op.getSummary().getRight())));
            }

            if (op.getDescription() != null && op.getDescription().isDifferent()) {
                changes.add(new MetadataChange(path, method, "Description", 
                        String.valueOf(op.getDescription().getLeft()), 
                        String.valueOf(op.getDescription().getRight())));
            }

            // Parameters
            if (op.getParameters() != null) {
                op.getParameters().getChanged().forEach(param -> {
                    if (param.getDescription() != null && param.getDescription().isDifferent()) {
                        changes.add(new MetadataChange(path, method, "Param: " + param.getName(), 
                                String.valueOf(param.getDescription().getLeft()), 
                                String.valueOf(param.getDescription().getRight())));
                    }
                });
            }

            // Responses Descriptions
            if (op.getApiResponses() != null) {
                op.getApiResponses().getChanged().forEach((code, resp) -> {
                    if (resp.getDescription() != null && resp.getDescription().isDifferent()) {
                        changes.add(new MetadataChange(path, method, "Response " + code + " Description",
                                String.valueOf(resp.getDescription().getLeft()),
                                String.valueOf(resp.getDescription().getRight())));
                    }
                    // Deep schema metadata
                    if (resp.getContent() != null) {
                        resp.getContent().getChanged().forEach((mediaType, content) -> {
                            if (content.getSchema() != null) {
                                extractSchemaMetadataChanges(path, method, "Response " + code + " Schema", content.getSchema(), changes);
                            }
                        });
                    }
                });
            }
        });
        return changes;
    }

    private void extractSchemaMetadataChanges(String path, String method, String prefix, ChangedSchema schema, List<MetadataChange> changes) {
        if (schema.getDescription() != null && schema.getDescription().isDifferent()) {
            changes.add(new MetadataChange(path, method, prefix + " Description",
                    String.valueOf(schema.getDescription().getLeft()),
                    String.valueOf(schema.getDescription().getRight())));
        }
        if (schema.getChangedProperties() != null) {
            schema.getChangedProperties().forEach((name, prop) -> {
                extractSchemaMetadataChanges(path, method, prefix + " -> " + name, prop, changes);
            });
        }
        if (schema.getItems() != null && schema.getItems().isDifferent()) {
            extractSchemaMetadataChanges(path, method, prefix + " (items)", schema.getItems(), changes);
        }
    }

    private void normalizeAllDescriptions(OpenAPI openAPI) {
        if (openAPI.getPaths() != null) {
            openAPI.getPaths().values().forEach(pathItem -> {
                pathItem.readOperationsMap().values().forEach(operation -> {
                    operation.setDescription(normalizeText(operation.getDescription()));
                    operation.setSummary(normalizeText(operation.getSummary()));
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(p -> p.setDescription(normalizeText(p.getDescription())));
                    }
                });
            });
        }
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            openAPI.getComponents().getSchemas().values().forEach(this::normalizeSchemaDescription);
        }
    }

    private void normalizeSchemaDescription(Schema schema) {
        if (schema == null) return;
        schema.setDescription(normalizeText(schema.getDescription()));
        if (schema.getProperties() != null) {
            ((Map<String, Schema>) schema.getProperties()).values().forEach(this::normalizeSchemaDescription);
        }
        if (schema.getItems() != null) {
            normalizeSchemaDescription(schema.getItems());
        }
    }

    private String normalizeText(String text) {
        if (text == null) return null;
        return text.replaceAll("\\s+", " ").trim();
    }

    private String renderToString(Render render, ChangedOpenApi diff) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        render.render(diff, writer);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void setOperationByMethod(PathItem pathItem, PathItem.HttpMethod method, Operation operation) {
        switch (method) {
            case GET -> pathItem.setGet(operation);
            case POST -> pathItem.setPost(operation);
            case PUT -> pathItem.setPut(operation);
            case DELETE -> pathItem.setDelete(operation);
            case PATCH -> pathItem.setPatch(operation);
            case HEAD -> pathItem.setHead(operation);
            case OPTIONS -> pathItem.setOptions(operation);
            case TRACE -> pathItem.setTrace(operation);
        }
    }

    private Operation findOperation(OpenAPI spec, String path, PathItem.HttpMethod method, String operationId) {
        if (operationId != null && !operationId.isEmpty()) {
            for (PathItem pi : spec.getPaths().values()) {
                for (Operation op : pi.readOperationsMap().values()) {
                    if (operationId.equals(op.getOperationId())) {
                        return op;
                    }
                }
            }
        }
        PathItem pi = spec.getPaths().get(path);
        if (pi != null) {
            return pi.readOperationsMap().get(method);
        }
        return null;
    }
}
