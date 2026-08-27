package com.dev.alex.mcp.tools;

import com.dev.alex.mcp.client.PortfolioApiClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * Reference data belonging to no single portfolio: the FX table, and the written
 * contract describing how to read every other response.
 * <p>
 * The conventions document is exposed twice on purpose — as a tool, so a model
 * can pull it mid-conversation, and as a resource, so a client can load it once
 * up front instead of re-reading the same caveats on each call.
 */
@Component
public class ReferenceTools {

    private static final String CONVENTIONS_PATH = "/api/v1/ai/conventions";

    private final PortfolioApiClient api;

    public ReferenceTools(PortfolioApiClient api) {
        this.api = api;
    }

    @McpTool(name = "get_fx_rates",
            description = """
                    Current exchange rates as units per 1 EUR, so EUR is always 1.0. Includes synthetic \
                    GBp and GBx entries (the GBP rate multiplied by 100) because London listings are quoted \
                    in pence, letting one formula handle every currency: \
                    amount_in_TARGET = amount * fxRates[TARGET] / fxRates[SOURCE]. A currency absent from \
                    the map has no published rate — say so rather than assuming parity.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Exchange rates",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getFxRates() {
        return api.get("/api/v1/ai/fx-rates");
    }

    @McpTool(name = "get_conventions",
            description = """
                    How to read every response from this server: the currency rules, what null means, what \
                    each asset and transaction type does, the known gaps in the data, and which endpoint \
                    answers what. Worth reading once before drawing conclusions about money, and worth \
                    re-reading if a figure looks wrong.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Data conventions",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getConventions() {
        return api.get(CONVENTIONS_PATH);
    }

    @McpResource(uri = "portfolio://conventions",
            name = "portfolio-conventions",
            title = "FinancePortfolio data conventions",
            description = "Currency handling, null semantics, asset and transaction type meanings, and "
                    + "known data gaps for every portfolio tool.",
            mimeType = "application/json")
    public String conventionsResource() {
        return api.get(CONVENTIONS_PATH);
    }
}
