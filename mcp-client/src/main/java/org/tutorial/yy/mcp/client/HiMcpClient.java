package org.tutorial.yy.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.function.Function;

/**
 * @author yyHuangfu
 * @create 2025/7/18
 */
public class HiMcpClient {

    public static McpSyncClient from(String url) {
        Function<McpSchema.ElicitRequest, McpSchema.ElicitResult> hiElicitationHandler =
                request -> {
                    System.out.println("Elicitation request: " + request);
                    // todo 实现用户交互逻辑
                    return McpSchema.ElicitResult.builder()
                            .message(McpSchema.ElicitResult.Action.ACCEPT)
                            .meta(request.meta())
                            .content(request.requestedSchema())
                            .build();
                };

        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(url)
                .sseEndpoint("/sse")
                .build();
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("hello-mcp-client", "1.0.0"))
                .elicitation(hiElicitationHandler)
                .build();
    }
}
