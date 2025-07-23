# HELLO-MCP
# What is MCP?
MCP is an open protocol that standardizes how applications provide context to LLMs. 
MCP provides a standardized way to connect AI models to **different data sources and tools**.
* **MCP Hosts**: Programs like Claude Desktop, IDEs, or AI tools that want to access data through MCP
* **MCP Clients**: Protocol clients that maintain 1:1 connections with servers
* **MCP Servers**: Lightweight programs that each expose specific capabilities through the standardized Model Context Protocol
* **Local Data Sources**: Your computer's files, databases, and services that MCP servers can securely access
* **Remote Services**: External systems available over the internet (e.g., through APIs) that MCP servers can connect to

# MCP Features
## Client
### Elicitation
> mcp client的elicitation机制，使mcp server在其已有功能的运行中，还可以内嵌地请求client收集用户输入信息，来完成更复杂的交互逻辑。

主要几个注意点：

1.McpServer要在每个工具执行的逻辑种，加入对应该工具的elicitation请求创建的逻辑
```java
// 定义该工具的elicitation请求
McpSchema.ElicitRequest elicitationRequest = McpSchema.ElicitRequest.builder()
        .message("请确认是否执行")
        .requestedSchema(
                Map.of("type", "boolean")
        )
        .build();

// 发送给client elicitation请求，等待回复
McpSchema.ElicitResult elicitationResponse = exchange.createElicitation(elicitationRequest);

if (elicitationResponse.action().equals(McpSchema.ElicitResult.Action.ACCEPT)) {
    // 用户接受该工具执行，继续执行
    return new McpSchema.CallToolResult(List.of(toolResult), false);
} else {
    // 用户不接收，返回用户取消执行话术
    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("用户不同意该工具执行")), false);
}
```

2.McpClient在初始化时，实现统一的elicitation请求的处理逻辑，也就是给用户判断，是否要执行该工具。
```java
// client初始化
public static McpSyncClient create(String url) {
    // 定义mcp server发送的elicitation请求处理逻辑
    Function<McpSchema.ElicitRequest, McpSchema.ElicitResult> hiElicitationHandler =
            request -> {
                // ...发送给用户，该elicitation请求确认是否执行的，前端展示样式
                if (接收到用户确认要执行) {
                    // 返回确认执行，给到mcp server
                    action = McpSchema.ElicitResult.Action.ACCEPT;
                } else {
                    // 告诉mcp server，用户拒绝执行
                    action = McpSchema.ElicitResult.Action.DECLINE;
                }
                // 返回elicitation的执行结果，也就是用户确认结果
                return McpSchema.ElicitResult.builder()
                        .message(action)
                        .content(request.requestedSchema())
                        .build();
            };
    return mcpClient; // 返回创建好的mcp client
}
```