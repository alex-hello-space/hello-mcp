package org.tutorial.yy.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
public class HelloMcpClient {
    public static void main(String[] args) {

        // SSE Client (ClientWebFluxSse.java)
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder("http://localhost:8080/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport).build();

        client.initialize();

        // List available tools
        McpSchema.ListToolsResult tools = client.listTools();
        System.out.println("Available tools: " + tools);

        client.ping();
        client.close();
    }
}
