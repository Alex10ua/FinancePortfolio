package com.dev.alex.mcp.tools;

import com.dev.alex.mcp.client.PortfolioApiClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dividend income, the payment calendar, and watchlist yield ranking. */
@Component
public class IncomeTools {

    private final PortfolioApiClient api;

    public IncomeTools(PortfolioApiClient api) {
        this.api = api;
    }

    @McpTool(name = "get_dividends",
            description = """
                    Dividend income for one portfolio: what each ticker has actually paid, the monthly, \
                    quarterly and yearly series, and the projected next-twelve-months income. Every series \
                    is kept inside a single currency, so a combined figure has to be converted first.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Dividend income",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getDividends(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/dividends", portfolioId);
    }

    @McpTool(name = "get_dividend_calendar",
            description = """
                    Expected dividend income by calendar month at the current holding sizes, with each \
                    position's per-share amount, share count, pre-multiplied total and currency. This is a \
                    repeating annual schedule inferred from each ticker's payment history, not dated events \
                    for one specific year — say so rather than implying exact payment dates.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Dividend calendar",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getDividendCalendar(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/dividend-calendar", portfolioId);
    }

    @McpTool(name = "get_watchlist",
            description = """
                    Tickers the user is watching but does not own, each with the yield they want before \
                    buying, the price that would deliver it, and how far away it is. Also ranks today's \
                    yield against that ticker's own history over 1Y, 3Y, 5Y, 10Y and all-time windows \
                    (min, quartiles, median, 90th percentile, current percentile), which is how to judge \
                    whether a yield is genuinely high for that specific stock.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Watchlist",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getWatchlist(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId,
            @McpToolParam(description = "Months of yield history per ticker. Default 60, capped at 240. "
                    + "The percentile statistics use the full series regardless.", required = false)
            Integer historyMonths) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("historyMonths", historyMonths);
        return api.get("/api/v1/ai/{portfolioId}/watchlist", query, portfolioId);
    }
}
