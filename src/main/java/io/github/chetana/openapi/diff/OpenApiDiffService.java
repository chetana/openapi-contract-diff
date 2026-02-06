package io.github.chetana.openapi.diff;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.output.ConsoleRender;
import org.openapitools.openapidiff.core.output.Render;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class OpenApiDiffService {

    public record DiffResult(String consoleReport, String metadataReport, boolean isDifferent) {}

    public DiffResult compare(String pmSpecContent, String generatedSpecUrl) throws Exception {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);

        OpenAPI pmOpenAPI = new OpenAPIV3Parser().readContents(pmSpecContent, null, options).getOpenAPI();
        OpenAPI genOpenAPI = new OpenAPIV3Parser().read(generatedSpecUrl, null, options);

        if (pmOpenAPI == null || genOpenAPI == null) {
            throw new IllegalArgumentException("Could not parse one of the OpenAPI specifications.");
        }

        normalizeAllDescriptions(pmOpenAPI);
        normalizeAllDescriptions(genOpenAPI);

        OpenAPI filteredGenOpenAPI = filterGeneratedOpenApi(pmOpenAPI, genOpenAPI);

        String pmJson = Json.pretty(pmOpenAPI);
        String genJson = Json.pretty(filteredGenOpenAPI);

        ChangedOpenApi diff = OpenApiCompare.fromContents(pmJson, genJson);

        String consoleReport = renderToString(new ConsoleRender(), diff);
        String metadataReport = extractMetadataChanges(diff);

        return new DiffResult(consoleReport, metadataReport, diff.isDifferent());
    }

    private OpenAPI filterGeneratedOpenApi(OpenAPI pmOpenAPI, OpenAPI genOpenAPI) {
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
                }
            });
            if (!genPathItem.readOperationsMap().isEmpty()) {
                newPaths.addPathItem(path, genPathItem);
            }
        });
        filteredGenOpenAPI.setPaths(newPaths);
        return filteredGenOpenAPI;
    }

    private String extractMetadataChanges(ChangedOpenApi diff) {
        StringBuilder metadataChanges = new StringBuilder();
        diff.getChangedOperations().forEach(op -> {
            boolean summaryChanged = op.getSummary() != null && op.getSummary().isDifferent();
            boolean descChanged = op.getDescription() != null && op.getDescription().isDifferent();

            if (summaryChanged || descChanged) {
                metadataChanges.append("#### ").append(op.getHttpMethod()).append(" `").append(op.getPathUrl()).append("`\n");
                if (summaryChanged) {
                    metadataChanges.append("- **Summary**:\n");
                    metadataChanges.append("    - **PM** : `").append(op.getSummary().getLeft()).append("`\n");
                    metadataChanges.append("    - **Gen**: `").append(op.getSummary().getRight()).append("`\n");
                }
                if (descChanged) {
                    metadataChanges.append("- **Description**:\n");
                    metadataChanges.append("    - **PM** :\n> ").append(op.getDescription().getLeft()).append("\n");
                    metadataChanges.append("    - **Gen**:\n> ").append(op.getDescription().getRight()).append("\n");
                }
                metadataChanges.append("\n");
            }

            // Parameters
            if (op.getParameters() != null) {
                op.getParameters().getChanged().forEach(param -> {
                    if (param.getDescription() != null && param.getDescription().isDifferent()) {
                        metadataChanges.append("#### ").append(op.getHttpMethod()).append(" `").append(op.getPathUrl())
                                .append("` (Parameter: `").append(param.getName()).append("`)\n");
                        metadataChanges.append("- **Description**:\n");
                        metadataChanges.append("    - **PM** : ").append(param.getDescription().getLeft()).append("\n");
                        metadataChanges.append("    - **Gen**: ").append(param.getDescription().getRight()).append("\n\n");
                    }
                });
            }
        });
        return metadataChanges.toString();
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
