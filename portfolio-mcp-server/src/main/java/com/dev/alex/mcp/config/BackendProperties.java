package com.dev.alex.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the backend is and who to log in as. Supplied through the MCP client's
 * env block (PORTFOLIO_API_BASE_URL / PORTFOLIO_USERNAME / PORTFOLIO_PASSWORD).
 */
@ConfigurationProperties(prefix = "portfolio.api")
public class BackendProperties {

    /** Backend root, no trailing slash, e.g. http://localhost:8080 */
    private String baseUrl = "http://localhost:8080";
    private String username = "";
    private String password = "";
    /** Per-request timeout; the watchlist and refresh paths are slow by nature. */
    private int timeoutSeconds = 30;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
