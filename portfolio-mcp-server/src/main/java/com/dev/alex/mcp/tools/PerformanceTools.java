package com.dev.alex.mcp.tools;

import com.dev.alex.mcp.client.PortfolioApiClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Performance and profit-and-loss over time.
 * <p>
 * Watch the naming in these responses: fields suffixed
 * {@code UnconvertedNativeSum} were added across currencies with no FX applied,
 * so they are exact only for a single-currency portfolio. The value series is
 * broken out per currency and does convert cleanly.
 */
@Component
public class PerformanceTools {

    private final PortfolioApiClient api;

    public PerformanceTools(PortfolioApiClient api) {
        this.api = api;
    }

    @McpTool(name = "get_performance",
            description = """
                    Performance of one portfolio: total invested, current value, unrealized and realized \
                    profit, dividends received, total return and XIRR (money-weighted annual return), plus \
                    the value series. Fields ending in UnconvertedNativeSum are cross-currency sums with no \
                    FX applied — for a portfolio holding more than one currency, rebuild them from the \
                    per-currency figures rather than quoting them directly.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Portfolio performance",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getPerformance(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId,
            @McpToolParam(description = "Window: 1W, 1M, 3M, YTD, 1Y or ALL. Defaults to ALL.",
                    required = false) String period,
            @McpToolParam(description = "Maximum points in the value series. Default 120, capped at 600.",
                    required = false) Integer maxPoints) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("period", period);
        query.put("maxPoints", maxPoints);
        return api.get("/api/v1/ai/{portfolioId}/performance", query, portfolioId);
    }

    @McpTool(name = "get_portfolio_history",
            description = """
                    Month-end value of one portfolio over time, each point split per currency so it can be \
                    converted properly. Use this for growth, drawdown and trend questions. The series is \
                    thinned evenly to maxPoints and always keeps the most recent point.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Value history",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getPortfolioHistory(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId,
            @McpToolParam(description = "Earliest date to include, yyyy-MM-dd. Omit for the whole history.",
                    required = false) String from,
            @McpToolParam(description = "Latest date to include, yyyy-MM-dd. Omit for up to today.",
                    required = false) String to,
            @McpToolParam(description = "Maximum points returned. Default 120, capped at 600.",
                    required = false) Integer maxPoints) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("from", from);
        query.put("to", to);
        query.put("maxPoints", maxPoints);
        return api.get("/api/v1/ai/{portfolioId}/history", query, portfolioId);
    }

    @McpTool(name = "get_realized_pnl",
            description = """
                    Realized profit and loss per currency, computed FIFO from positions that were sold in \
                    whole or in part. This is money already banked; profit on positions still held is the \
                    unrealizedProfit field on each position. Total profit is the two added together.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Realized P&L",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getRealizedPnl(
            @McpToolParam(description = "Portfolio id from list_portfolios", required = true)
            String portfolioId) {
        return api.get("/api/v1/ai/{portfolioId}/realized-pnl", portfolioId);
    }
}
