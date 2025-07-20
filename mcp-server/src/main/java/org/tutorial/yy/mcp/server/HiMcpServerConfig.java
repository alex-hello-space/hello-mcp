package org.tutorial.yy.mcp.server;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tutorial.yy.mcp.server.capabilities.Tools;

import java.util.List;

/**
 * @author yyHuangfu
 * @create 2025/7/17
 */
@Configuration
@EnableWebMvc
public class HiMcpServerConfig implements WebMvcConfigurer {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> getTools() {
        return List.of(Tools.getSpec());
    }
}
