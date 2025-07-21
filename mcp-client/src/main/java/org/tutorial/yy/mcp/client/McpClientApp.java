package org.tutorial.yy.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
public class McpClientApp {
    private static final String TOOL_NAME = "需要用户确认的tool";
    private static final Map<String, Object> TOOL_ARGUMENT = Map.of("arg", "我是模型生成的参数");

    public static void main(String[] args) {
        // client能力注册
        McpSyncClient client = HellpMcpClientFactory.create("http://localhost:8080");
        client.initialize();

        // List available tools
        McpSchema.ListToolsResult tools = client.listTools();
        System.out.println("[McpClient] Available tools: " + tools);

        if (!tools.tools().isEmpty()) {
            // 构造工具调用参数
            McpSchema.CallToolRequest toolCall = McpSchema.CallToolRequest.builder()
                    .name(TOOL_NAME)
                    .arguments(TOOL_ARGUMENT)
                    .build();

            // 调用服务端执行工具
            McpSchema.CallToolResult toolResponse = client.callTool(toolCall);
            System.out.println("[McpClient] Tool response: " + toolResponse);
        }

        client.closeGracefully();
    }
}
