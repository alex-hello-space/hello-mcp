让我详细解释一下这个持续监听机制是如何实现的。

## 持续监听的核心机制

### 1. SSE (Server-Sent Events) 技术基础

首先，需要了解SSE是一种服务器向客户端推送数据的技术：
- 客户端发起一个HTTP连接请求
- 服务器保持连接打开
- 服务器可以持续向客户端发送数据事件
- 客户端通过EventSource API接收事件

### 2. HttpClientSseClientTransport.connect() 方法详解

让我们逐步分析这个方法的实现：

```java
@Override
public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
    return Mono.create(sink -> {
        // 1. 构建SSE连接请求
        HttpRequest request = requestBuilder.copy()
                .uri(Utils.resolveUri(this.baseUri, this.sseEndpoint))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        // 2. 发起异步HTTP请求并处理响应流
        Disposable connection = Flux.<ResponseEvent>create(sseSink -> this.httpClient
                        .sendAsync(request, responseInfo -> ResponseSubscribers.sseToBodySubscriber(responseInfo, sseSink))
                        .exceptionallyCompose(e -> {
                            sseSink.error(e);
                            return CompletableFuture.failedFuture(e);
                        }))
                // 3. 处理响应事件
                .map(responseEvent -> (ResponseSubscribers.SseResponseEvent) responseEvent)
                .flatMap(responseEvent -> {
                    if (isClosing) {
                        return Mono.empty();
                    }

                    int statusCode = responseEvent.responseInfo().statusCode();

                    if (statusCode >= 200 && statusCode < 300) {
                        try {
                            // 处理endpoint事件
                            if (ENDPOINT_EVENT_TYPE.equals(responseEvent.sseEvent().event())) {
                                String messageEndpointUri = responseEvent.sseEvent().data();
                                if (this.messageEndpointSink.tryEmitValue(messageEndpointUri).isSuccess()) {
                                    sink.success(); // 连接建立成功
                                    return Flux.empty();
                                } else {
                                    sink.error(new McpError("Failed to handle SSE endpoint event"));
                                }
                            } 
                            // 处理message事件（这是持续监听的关键）
                            else if (MESSAGE_EVENT_TYPE.equals(responseEvent.sseEvent().event())) {
                                JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper,
                                        responseEvent.sseEvent().data());
                                sink.success(); // 连接建立成功（如果这是第一次）
                                return Flux.just(message); // 关键：将消息传递给下一个处理步骤
                            } else {
                                logger.debug("Received unrecognized SSE event type: {}", responseEvent.sseEvent());
                                sink.success();
                            }
                        } catch (IOException e) {
                            logger.error("Error processing SSE event", e);
                            sink.error(new McpError("Error processing SSE event"));
                        }
                    }
                    return Flux.<JSONRPCMessage>error(
                            new RuntimeException("Failed to send message: " + responseEvent));
                })
                // 4. 关键步骤：将每个消息传递给handler处理
                .flatMap(jsonRpcMessage -> handler.apply(Mono.just(jsonRpcMessage)))
                // 5. 错误处理
                .onErrorComplete(t -> {
                    if (!isClosing) {
                        logger.warn("SSE stream observed an error", t);
                        sink.error(t);
                    }
                    return true;
                })
                // 6. 资源清理
                .doFinally(s -> {
                    Disposable ref = this.sseSubscription.getAndSet(null);
                    if (ref != null && !ref.isDisposed()) {
                        ref.dispose();
                    }
                })
                .contextWrite(sink.contextView())
                .subscribe(); // 7. 启动订阅

        this.sseSubscription.set(connection); // 保存订阅引用
    });
}
```


### 3. 持续监听的关键点

#### 3.1 Flux.create() 创建无限流
```java
Flux.<ResponseEvent>create(sseSink -> {
    // 这里创建了一个可能无限的流
    this.httpClient.sendAsync(request, responseInfo -> ResponseSubscribers.sseToBodySubscriber(responseInfo, sseSink))
})
```

[Flux.create()](file://reactor\core\publisher\Flux.java#L49-L49)创建了一个响应式流，它会持续接收SSE事件，直到连接关闭。

#### 3.2 ResponseSubscribers.sseToBodySubscriber
这个订阅者负责将HTTP响应体转换为SSE事件流：
- 持续读取响应体
- 解析SSE格式的数据
- 将每个事件通过`sseSink.next()`发射出去

#### 3.3 FlatMap处理每个事件
```java
.flatMap(responseEvent -> {
    // 处理每个SSE事件
    if (MESSAGE_EVENT_TYPE.equals(responseEvent.sseEvent().event())) {
        // 将消息包装成Flux继续传递
        return Flux.just(message);
    }
})
```

每次收到SSE消息事件时，都会创建一个包含该消息的Flux并继续处理。

#### 3.4 Handler应用
```java
.flatMap(jsonRpcMessage -> handler.apply(Mono.just(jsonRpcMessage)))
```

这是关键部分，将每个JSON-RPC消息传递给外部提供的处理器函数。

### 4. McpClientSession中的处理器

在[McpClientSession](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\spec\McpClientSession.java#L38-L307)构造函数中：
```java
this.transport.connect(mono -> mono.doOnNext(this::handle)).transform(connectHook).subscribe();
```


这里的`mono -> mono.doOnNext(this::handle)`就是处理器：
- `this::handle`指向[McpClientSession.handle](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\spec\McpClientSession.java#L146-L179)方法
- 每当有新消息到达时，[handle](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\spec\McpClientSession.java#L146-L179)方法就会被调用

### 5. 完整的监听流程

1. **连接建立**：调用[connect()](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\client\transport\HttpClientSseClientTransport.java#L354-L427)方法发起SSE连接
2. **持续接收**：服务器通过SSE连接持续发送事件
3. **事件解析**：[ResponseSubscribers.sseToBodySubscriber](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\client\transport\ResponseSubscribers.java#L50-L53)解析每个SSE事件
4. **事件分发**：[connect()](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\client\transport\HttpClientSseClientTransport.java#L354-L427)方法将每个消息事件通过`.flatMap(jsonRpcMessage -> handler.apply(...))`传递给处理器
5. **消息处理**：[McpClientSession.handle](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\spec\McpClientSession.java#L146-L179)方法处理每个消息（响应、请求或通知）
6. **持续循环**：这个过程一直持续，直到连接被显式关闭

### 6. 资源管理和生命周期

```java
.doFinally(s -> {
    Disposable ref = this.sseSubscription.getAndSet(null);
    if (ref != null && !ref.isDisposed()) {
        ref.dispose();
    }
})
```


通过[doFinally](file://reactor\core\publisher\Mono.java#L152-L152)确保在流结束时正确清理资源。

## 总结

持续监听机制的核心是：
1. 使用SSE技术建立长连接
2. 通过[Flux.create()](file://reactor\core\publisher\Flux.java#L49-L49)创建一个可能无限的响应式流
3. 使用自定义Subscriber ([ResponseSubscribers.sseToBodySubscriber](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\client\transport\ResponseSubscribers.java#L50-L53))持续解析SSE事件
4. 通过响应式操作符链([flatMap](file://reactor\core\publisher\Flux.java#L132-L132))将每个事件传递给外部处理器
5. 处理器([McpClientSession.handle](file://D:\projects\IdeaProjects\hello-mcp\mcp-sdk\mcp\src\main\java\io\modelcontextprotocol\spec\McpClientSession.java#L146-L179))处理所有类型的消息
6. 通过适当的资源管理确保连接可以被正确关闭

这就是为什么它能实现持续监听所有从服务器传入消息的原因。