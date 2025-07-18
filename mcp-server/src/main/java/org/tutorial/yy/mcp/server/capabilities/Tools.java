package org.tutorial.yy.mcp.server.capabilities;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
public class Tools {
    public static McpServerFeatures.SyncToolSpecification getSpec() {
        String schema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:Operation",
                  "properties" : {
                    "query" : {
                      "type" : "string"
                    }
                  }
                }
                """;
        McpSchema.ToolAnnotations annotations = new McpSchema.ToolAnnotations(
                "query",
                true,
                true,
                true,
                true,
                true
        );


        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("query")
                .description("Query Tool")
                .inputSchema(schema)
                .annotations(annotations)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) -> {
                            // Implement the tool logic here
                            String query = request.arguments().get("query").toString();
                            McpSchema.TextContent result = new McpSchema.TextContent("Query result for: " + query);
                            return new McpSchema.CallToolResult(List.of(result), false);
                        }
                ).build();
    }
}
