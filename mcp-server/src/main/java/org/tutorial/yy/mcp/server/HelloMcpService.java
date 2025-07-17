package org.tutorial.yy.mcp.server;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class HelloMcpService {

    @Tool(description = "Get weather forecast for a specific latitude/longitude")
    public String getWeatherForecastByLocation(
            double latitude,   // Latitude coordinate
            double longitude   // Longitude coordinate
    ) {
        return "getWeatherForecastByLocation";
    }

    @Tool(description = "Get weather alerts for a US state")
    public String getAlerts(
            @ToolParam(description = "Two-letter US state code (e.g. CA, NY)") String state
    ) {
        return "getAlerts";
    }

}