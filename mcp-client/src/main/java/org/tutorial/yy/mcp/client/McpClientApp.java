package org.tutorial.yy.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
public class McpClientApp {
    private static final Map<String, Object> TOOL_ARGUMENT = Map.of("query", "I'm argument form LLM");

    public static void main(String[] args) {
        McpSyncClient client = HiMcpClient.createMcpClient("http://localhost:8080");
        client.initialize();

        // List available tools
        McpSchema.ListToolsResult tools = client.listTools();
        System.out.println("Available tools: " + tools);

        if (!tools.tools().isEmpty()) {
            // 构造工具调用参数
            McpSchema.CallToolRequest toolCall = McpSchema.CallToolRequest.builder()
                    .name(TOOL_NAME)
                    .arguments(TOOL_ARGUMENT)
                    .build();

            // 调用服务端执行工具
            McpSchema.CallToolResult toolResponse = client.callTool(toolCall);
            System.out.println("Tool response: " + toolResponse);
        }

        System.out.println("Ping: " + client.ping());
        client.closeGracefully();
    }
}
