package org.tutorial.yy.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
@Configuration
@EnableWebMvc
public class McpServerConfig implements WebMvcConfigurer {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> getTools() {
        return List.of(HelloMcpToolSpec.getSpec());
    }

    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {
        // 根据application.yml中的配置创建WebMvcSseServerTransportProvider
        return new WebMvcSseServerTransportProvider(
                new ObjectMapper(), 
                "", 
                "/mcp/message", 
                "/sse");
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpServer(WebMvcSseServerTransportProvider transportProvider) {
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("hello-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)             // Enable tool support
                        .build())
                .build();

        // Register tools from the getTools bean
        getTools().forEach(syncServer::addTool);
        return syncServer;
    }
}