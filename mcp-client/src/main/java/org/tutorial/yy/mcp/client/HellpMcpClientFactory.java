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
public class HellpMcpClientFactory {

    public static McpSyncClient create(String url) {
        Function<McpSchema.ElicitRequest, McpSchema.ElicitResult> hiElicitationHandler =
                request -> {
                    System.out.println("[McpClient]Elicitation请求: " + request);
                    System.out.print("[McpClient]是否允许此请求？(y/n): ");
                    String input = new java.util.Scanner(System.in).nextLine().trim();

                    McpSchema.ElicitResult.Action action;
                    if ("y".equalsIgnoreCase(input)) {
                        action = McpSchema.ElicitResult.Action.ACCEPT;
                    } else {
                        action = McpSchema.ElicitResult.Action.DECLINE;
                    }

                    return McpSchema.ElicitResult.builder()
                            .message(action)
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
