package org.tutorial.yy.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
public class McpClientApp {
    public static void main(String[] args) {
        McpSyncClient client = HiMcpClient.from("http://localhost:8080");
        client.initialize();

        // List available tools
        McpSchema.ListToolsResult tools = client.listTools();
        System.out.println("Available tools: " + tools);

        System.out.println("Ping: " + client.ping());
        client.close();
    }
}
