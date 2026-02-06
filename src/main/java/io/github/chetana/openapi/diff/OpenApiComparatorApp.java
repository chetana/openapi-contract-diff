package io.github.chetana.openapi.diff;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.output.ConsoleRender;
import org.openapitools.openapidiff.core.output.MarkdownRender;
import org.openapitools.openapidiff.core.output.Render;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

/**
 * Main application to compare two OpenAPI specifications with smart mapping by operationId
 * and normalized description comparison.
 */
public class OpenApiComparatorApp {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar djust-api-comparator.jar <pm-spec-path> <generated-spec-url-or-path> [output-markdown-file]");
            System.exit(1);
        }

        String pmSpecPath = args[0];
        String generatedSpecUrl = args[1];
        String outputMarkdown = args.length > 2 ? args[2] : null;
        if (outputMarkdown != null && !outputMarkdown.toLowerCase().endsWith(".md")) {
            outputMarkdown += ".md";
        }

        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            
            OpenAPI pmOpenAPI = new OpenAPIV3Parser().read(pmSpecPath, null, options);
            OpenAPI genOpenAPI = new OpenAPIV3Parser().read(generatedSpecUrl, null, options);

            if (pmOpenAPI == null || genOpenAPI == null) {
                System.err.println("Error: Could not parse one of the OpenAPI specifications.");
                System.exit(1);
            }

            // Normalisation des descriptions pour être indulgent sur le formatage
            normalizeAllDescriptions(pmOpenAPI);
            normalizeAllDescriptions(genOpenAPI);

            // --- Smart Mapping & Filtering ---
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

            // Convert to JSON strings for openapi-diff
            String pmJson = Json.pretty(pmOpenAPI);
            String genJson = Json.pretty(filteredGenOpenAPI);

            // Compare using fromContents
            ChangedOpenApi diff = OpenApiCompare.fromContents(pmJson, genJson);

            if (diff.isDifferent()) {
                System.out.println("\n--- Comparison Results (Filtered & Normalized) ---");
                String mainReport = renderToString(new ConsoleRender(), diff);
                System.out.println(mainReport);

                // Manual check for Summary and Description changes (often hidden by ConsoleRender)
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

                    // Parameter descriptions
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

                    // Response schema descriptions
                    if (op.getApiResponses() != null && op.getApiResponses().getChanged() != null) {
                        op.getApiResponses().getChanged().forEach((status, response) -> {
                            if (response.getContent() != null && response.getContent().getChanged() != null) {
                                response.getContent().getChanged().forEach((mediaType, changedMediaType) -> {
                                    reportSchemaDescriptionChanges(metadataChanges, op, "Response " + status, changedMediaType.getSchema());
                                });
                            }
                        });
                    }

                    // Request body schema descriptions
                    if (op.getRequestBody() != null && op.getRequestBody().getContent() != null && op.getRequestBody().getContent().getChanged() != null) {
                        op.getRequestBody().getContent().getChanged().forEach((mediaType, changedMediaType) -> {
                            reportSchemaDescriptionChanges(metadataChanges, op, "Request Body", changedMediaType.getSchema());
                        });
                    }
                });

                // Global Schema changes
                if (diff.getChangedSchemas() != null) {
                    diff.getChangedSchemas().forEach(changedSchema -> {
                        String schemaName = changedSchema.getNewSchema() != null ? changedSchema.getNewSchema().getName() : 
                                           (changedSchema.getOldSchema() != null ? changedSchema.getOldSchema().getName() : "Unknown");
                        reportGlobalSchemaDescriptionChanges(metadataChanges, schemaName, changedSchema);
                    });
                }

                if (metadataChanges.length() > 0) {
                    System.out.println("--------------------------------------------------------------------------");
                    System.out.println("--                      Metadata Changes (Non-Breaking)         --");
                    System.out.println("--------------------------------------------------------------------------");
                    System.out.print(metadataChanges.toString());
                }

                if (outputMarkdown != null) {
                    String mdReport = renderToString(new MarkdownRender(), diff);
                    if (metadataChanges.length() > 0) {
                        mdReport += "\n\n### Metadata Changes\n" + metadataChanges.toString();
                    }
                    Files.writeString(Paths.get(outputMarkdown), mdReport);
                    System.out.println("\nDetailed report generated: " + outputMarkdown);
                }
            } else {
                System.out.println("\n✅ Match! The generated API respects the PM contract (descriptions normalized).");
            }

        } catch (Exception e) {
            System.err.println("Error during comparison: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void normalizeAllDescriptions(OpenAPI openAPI) {
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
            openAPI.getComponents().getSchemas().values().forEach(OpenApiComparatorApp::normalizeSchemaDescription);
        }
    }

    private static void normalizeSchemaDescription(Schema schema) {
        if (schema == null) return;
        schema.setDescription(normalizeText(schema.getDescription()));
        if (schema.getProperties() != null) {
            ((Map<String, Schema>) schema.getProperties()).values().forEach(OpenApiComparatorApp::normalizeSchemaDescription);
        }
        if (schema.getItems() != null) {
            normalizeSchemaDescription(schema.getItems());
        }
    }

    private static String normalizeText(String text) {
        if (text == null) return null;
        // Normalise seulement les espaces pour éviter les faux positifs d'indentation/retours à la ligne
        // mais garde les balises HTML (<br>, etc.) et la casse pour assurer une identité visuelle.
        return text.replaceAll("\\s+", " ")
                   .trim();
    }

    private static void reportSchemaDescriptionChanges(StringBuilder metadataChanges, org.openapitools.openapidiff.core.model.ChangedOperation op, String context, org.openapitools.openapidiff.core.model.ChangedSchema schema) {
        if (schema == null) return;

        if (schema.getDescription() != null && schema.getDescription().isDifferent()) {
            metadataChanges.append("#### ").append(op.getHttpMethod()).append(" `").append(op.getPathUrl()).append("`\n");
            metadataChanges.append("- **").append(context).append(" Description**:\n");
            metadataChanges.append("    - **PM** : ").append(schema.getDescription().getLeft()).append("\n");
            metadataChanges.append("    - **Gen**: ").append(schema.getDescription().getRight()).append("\n\n");
        }

        if (schema.getChangedProperties() != null) {
            schema.getChangedProperties().forEach((propName, changedProp) -> {
                reportSchemaDescriptionChanges(metadataChanges, op, context + " -> " + propName, changedProp);
            });
        }

        if (schema.getItems() != null) {
            reportSchemaDescriptionChanges(metadataChanges, op, context + " items", schema.getItems());
        }
    }

    private static void reportGlobalSchemaDescriptionChanges(StringBuilder metadataChanges, String schemaName, org.openapitools.openapidiff.core.model.ChangedSchema schema) {
        if (schema == null) return;

        if (schema.getDescription() != null && schema.getDescription().isDifferent()) {
            metadataChanges.append("#### Schema `").append(schemaName).append("`\n");
            metadataChanges.append("- **Description**:\n");
            metadataChanges.append("    - **PM** : ").append(schema.getDescription().getLeft()).append("\n");
            metadataChanges.append("    - **Gen**: ").append(schema.getDescription().getRight()).append("\n\n");
        }

        if (schema.getChangedProperties() != null) {
            schema.getChangedProperties().forEach((propName, changedProp) -> {
                reportGlobalSchemaDescriptionChanges(metadataChanges, schemaName + " -> " + propName, changedProp);
            });
        }

        if (schema.getItems() != null) {
            reportGlobalSchemaDescriptionChanges(metadataChanges, schemaName + " items", schema.getItems());
        }
    }

    private static String renderToString(Render render, ChangedOpenApi diff) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        render.render(diff, writer);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static void setOperationByMethod(PathItem pathItem, PathItem.HttpMethod method, Operation operation) {
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

    private static Operation findOperation(OpenAPI spec, String path, PathItem.HttpMethod method, String operationId) {
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
