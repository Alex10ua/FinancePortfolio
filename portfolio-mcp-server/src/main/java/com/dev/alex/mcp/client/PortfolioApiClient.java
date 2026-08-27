package com.dev.alex.mcp.client;

import com.dev.alex.mcp.config.BackendProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only route from this process to the portfolio backend.
 * <p>
 * <strong>Read-only by construction.</strong> The single data method is
 * {@link #get}; there is no post/put/delete to call, so no tool can modify a
 * portfolio however it is prompted. The one non-GET request is the login POST,
 * which authenticates and writes nothing.
 * <p>
 * Auth mirrors what the repo's backfill scripts do: Spring Security form login
 * returns 200 with a {@code JSESSIONID} cookie, which is then sent on every
 * request. Sessions are in-memory server-side with a 30-minute idle timeout and
 * die on a backend restart, so a 401 is expected in normal operation and is
 * handled by logging in again and retrying once.
 */
@Component
public class PortfolioApiClient {

    private static final Logger log = LoggerFactory.getLogger(PortfolioApiClient.class);

    private final RestClient http;
    private final BackendProperties properties;
    private final AtomicReference<String> sessionCookie = new AtomicReference<>();

    public PortfolioApiClient(RestClient portfolioRestClient, BackendProperties properties) {
        this.http = portfolioRestClient;
        this.properties = properties;
    }

    /**
     * GET a backend path and return its raw JSON body.
     *
     * @param pathTemplate path with {@code {placeholders}}, e.g. {@code /api/v1/ai/{portfolioId}/snapshot}
     * @param query        query parameters; null values are dropped
     * @param uriVars      values for the placeholders, URL-encoded by the builder.
     *                     Portfolio ids are a UUID concatenated with the portfolio
     *                     name, so they routinely contain characters that must be
     *                     encoded — never build these paths by string concatenation.
     */
    public String get(String pathTemplate, Map<String, Object> query, Object... uriVars) {
        ensureSession();
        Response response = exchange(pathTemplate, query, uriVars);
        if (response.status() == 401) {
            log.info("Backend session expired, logging in again");
            login();
            response = exchange(pathTemplate, query, uriVars);
        }
        return unwrap(response, pathTemplate);
    }

    public String get(String pathTemplate, Object... uriVars) {
        return get(pathTemplate, Map.of(), uriVars);
    }

    // ---------------------------------------------------------------- internals

    private record Response(int status, String body) {
    }

    private Response exchange(String pathTemplate, Map<String, Object> query, Object... uriVars) {
        return http.get()
                .uri(builder -> buildUri(builder, pathTemplate, query, uriVars))
                .header(HttpHeaders.COOKIE, sessionCookie.get())
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) ->
                        new Response(response.getStatusCode().value(), readBody(response.getBody())));
    }

    private java.net.URI buildUri(UriBuilder builder, String pathTemplate,
                                  Map<String, Object> query, Object... uriVars) {
        builder.path(pathTemplate);
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null) builder.queryParam(key, value);
            });
        }
        return builder.build(uriVars);
    }

    private String unwrap(Response response, String path) {
        int status = response.status();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        throw new IllegalStateException(switch (status) {
            case 401 -> "The portfolio backend rejected the credentials. Check PORTFOLIO_USERNAME and "
                    + "PORTFOLIO_PASSWORD in the MCP server configuration.";
            case 403 -> "That portfolio belongs to a different user, so it cannot be read.";
            case 404 -> "The backend has no data for " + path
                    + ". For a ticker this means no provider has fetched it yet.";
            default -> "The portfolio backend returned HTTP " + status + " for " + path
                    + (response.body() == null || response.body().isBlank() ? "" : ": " + response.body());
        });
    }

    private void ensureSession() {
        if (sessionCookie.get() == null) {
            login();
        }
    }

    /**
     * The one non-GET call in this class. Form login, not a data mutation:
     * SecurityConfig answers 200 on success and 401 on failure rather than
     * redirecting, and CSRF is disabled so no token is needed.
     */
    private synchronized void login() {
        if (!properties.hasCredentials()) {
            throw new IllegalStateException(
                    "No backend credentials configured. Set PORTFOLIO_USERNAME and PORTFOLIO_PASSWORD "
                            + "in the MCP server's environment.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", properties.getUsername());
        form.add("password", properties.getPassword());

        String cookie = http.post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 401) {
                        throw new IllegalStateException("The portfolio backend rejected the credentials in "
                                + "PORTFOLIO_USERNAME / PORTFOLIO_PASSWORD.");
                    }
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("Login to the portfolio backend at "
                                + properties.getBaseUrl() + " failed with HTTP " + status
                                + ". Is the backend running?");
                    }
                    return extractSessionCookie(response.getHeaders());
                });

        if (cookie == null) {
            throw new IllegalStateException("The backend accepted the login but returned no JSESSIONID cookie.");
        }
        sessionCookie.set(cookie);
        log.info("Authenticated to {} as {}", properties.getBaseUrl(), properties.getUsername());
    }

    private static String extractSessionCookie(HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        for (String value : setCookies) {
            if (value.startsWith("JSESSIONID=")) {
                int end = value.indexOf(';');
                return end < 0 ? value : value.substring(0, end);
            }
        }
        return null;
    }

    private static String readBody(java.io.InputStream body) {
        if (body == null) return "";
        try {
            return StreamUtils.copyToString(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the backend response", e);
        }
    }
}
