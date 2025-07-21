package org.tutorial.yy.mcp.server.capabilities;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
public class Tools {
    public static final String TOOL_NAME = "tool_need_elicitation";

    public static McpServerFeatures.SyncToolSpecification getSpec() {
        String schema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:Operation",
                  "properties" : {
                    "arg" : {
                      "type" : "string"
                    }
                  }
                }
                """;
        McpSchema.ToolAnnotations annotations = new McpSchema.ToolAnnotations(
                TOOL_NAME,
                true,
                true,
                true,
                true,
                true
        );


        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(TOOL_NAME)
                .description("tool need elicitation")
                .inputSchema(schema)
                .annotations(annotations)
                .meta(Map.of("author", "yyHuangfu",
                        "filter", "pc_only"))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(
                        (exchange, request) -> {
                            // 实现工具执行逻辑
                            String result = "";
                            // 进行确认操作
                            McpSchema.ElicitRequest elicitationRequest = McpSchema.ElicitRequest.builder()
                                    .message("请确认是否执行")
                                    .requestedSchema(
                                            Map.of("type", "boolean")
                                    )
                                    .build();

                            // Send elicitation request and wait for response
                            McpSchema.ElicitResult elicitationResponse = exchange.createElicitation(elicitationRequest);

                            if (elicitationResponse.action().equals(McpSchema.ElicitResult.Action.ACCEPT)) {
                                // 用户接受，继续执行
                                result = request.arguments().get("query").toString();
                            } else {
                                // 用户不接收，返回取消结果
                                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Query cancelled")), false);
                            }

                            McpSchema.TextContent toolResult = new McpSchema.TextContent("Query result for: " + result);
                            return new McpSchema.CallToolResult(List.of(toolResult), false);
                        }
                ).build();
    }
}