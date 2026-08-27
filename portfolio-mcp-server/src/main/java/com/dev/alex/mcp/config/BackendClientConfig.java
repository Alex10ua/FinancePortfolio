package com.dev.alex.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client pointed at the portfolio backend.
 * <p>
 * Timeouts matter more than usual here: an MCP client is waiting on a tool call
 * synchronously, so a hung request stalls the conversation rather than a page.
 */
@Configuration
@EnableConfigurationProperties(BackendProperties.class)
public class BackendClientConfig {

    @Bean
    public RestClient portfolioRestClient(BackendProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
