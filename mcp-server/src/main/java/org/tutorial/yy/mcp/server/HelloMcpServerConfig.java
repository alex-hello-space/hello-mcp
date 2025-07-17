package org.tutorial.yy.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
@Configuration
@EnableWebMvc
public class HelloMcpServerConfig implements WebMvcConfigurer {

    @Bean
    public HelloMcpService helloMcpService() {
        return new HelloMcpService();
    }

    @Bean
    public McpSyncServer mcpServer(HttpServletSseServerTransportProvider transport, HelloMcpService helloMcpService) {

        // Create a server with custom configurationMcpSyncServer syncServer = McpServer.sync(transport)
        McpSyncServer syncServer = McpServer.sync(transport)
                .serverInfo("my-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(false, true)  // Enable resource support
                        .tools(true)             // Enable tool support
                        .prompts(true)           // Enable prompt support
                        .logging()               // Enable logging support
                        .completions()           // Enable completions support
                        .build())
                .build();

        // Register tools, resources, and prompts
        syncServer.addTool(syncToolSpecification);
        return syncServer;
        // Close the server when done
        // syncServer.close();
    }

    @Bean
    public HttpServletSseServerTransportProvider servletSseServerTransportProvider() {
        return new HttpServletSseServerTransportProvider(new ObjectMapper(), "/mcp/message");
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> customServletBean(HttpServletSseServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider);
    }
}
