package com.dev.alex.mcp.tools;

import com.dev.alex.mcp.client.PortfolioApiClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What is held, what it cost and what it is worth.
 * <p>
 * Every response is the backend's envelope: {@code data} plus {@code fxRates},
 * {@code baseCurrency}, {@code conversionFormula} and {@code notes}. Money is
 * always in its own native currency and is never pre-converted, so a total that
 * spans currencies has to be built with the supplied rates.
 */
@Component
public class CoreTools {

    private final PortfolioApiClient api;

    public CoreTools(PortfolioApiClient api) {
        this.api = api;
    }

    @McpTool(name = "list_portfolios",
            description = """
                    List every portfolio the user owns, with its id, position count, the currencies its \
                    figures are quoted in, and the currency the user reads totals in. Start here when you \
                    do not already have a portfolioId — every other portfolio tool needs one.""",
            annotations = @McpTool.McpAnnotations(
                    title = "List portfolios",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String listPortfolios() {
        return api.get("/api/v1/ai/portfolios");
    }

    @McpTool(name = "get_portfolio_snapshot",
            description = """
                    The complete current state of one portfolio in a single call: every position with its \
                    cost, value, profit, yield, sector, tags and price freshness; manual cash and derived \
                    cash balance; realized profit and loss; and value totals already grouped by currency. \
                    Prefer this over several narrower calls. Amounts are in each asset's native currency — \
                    convert with the fxRates map in the response.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Portfolio snapshot",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getPortfolioSnapshot(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/snapshot", portfolioId);
    }

    @McpTool(name = "get_positions",
            description = """
                    Just the holdings of one portfolio, without cash or profit-and-loss totals. Use this \
                    instead of the snapshot when the question is only about what is owned, or when the \
                    portfolio is large enough that the full snapshot is unwieldy.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Positions",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getPositions(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/positions", portfolioId);
    }

    @McpTool(name = "get_diversification",
            description = """
                    How the portfolio's value splits across countries, sectors, industries and individual \
                    tickers. Each bucket keeps its amounts separated per currency so they can be converted \
                    before being compared. Tickers with no classification are listed separately.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Diversification",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getDiversification(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/diversification", portfolioId);
    }

    @McpTool(name = "get_tags",
            description = """
                    Holdings grouped by the user's own tags, with each group's value per currency. Tags are \
                    free-form labels the user attaches to tickers (themes, convictions, accounts) and are \
                    the right way to answer questions phrased in the user's own categories.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Holdings by tag",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getTags(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/tags", portfolioId);
    }

    @McpTool(name = "get_transactions",
            description = """
                    Transaction history for one portfolio, newest first, with optional filters and paging. \
                    BUY and SELL move share counts; DIVIDEND and TAX are cash events that do not; DEPOSIT \
                    and WITHDRAWAL are cash only and are not holdings. Each row carries the FX rate for its \
                    currency.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Transactions",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getTransactions(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId,
            @McpToolParam(description = "Calendar year to restrict to, e.g. 2026. Omit for all years.",
                    required = false) Integer year,
            @McpToolParam(description = "Single ticker symbol to restrict to. Omit for all.",
                    required = false) String ticker,
            @McpToolParam(description = "BUY, SELL, DIVIDEND, TAX, DEPOSIT or WITHDRAWAL. Omit for all.",
                    required = false) String type,
            @McpToolParam(description = "Rows to skip, for paging. Default 0.", required = false) Integer offset,
            @McpToolParam(description = "Maximum rows to return. Default 200, capped at 1000.",
                    required = false) Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("year", year);
        query.put("ticker", ticker);
        query.put("type", type);
        query.put("offset", offset);
        query.put("limit", limit);
        return api.get("/api/v1/ai/{portfolioId}/transactions", query, portfolioId);
    }
}
