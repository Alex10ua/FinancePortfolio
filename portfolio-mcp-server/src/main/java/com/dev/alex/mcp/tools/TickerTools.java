package com.dev.alex.mcp.tools;

import com.dev.alex.mcp.client.PortfolioApiClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Research on a single symbol. These are market facts rather than user data, so
 * they need no portfolio id — but they only cover tickers this installation has
 * already fetched. An unknown symbol returns an error saying so; it does not
 * trigger a provider call.
 */
@Component
public class TickerTools {

    private final PortfolioApiClient api;

    public TickerTools(PortfolioApiClient api) {
        this.api = api;
    }

    @McpTool(name = "get_ticker",
            description = """
                    Market data and Yahoo key statistics for one symbol: price and day change, sector, \
                    country, industry, shares outstanding, and roughly 75 statistics covering margins, \
                    returns, valuation multiples, balance sheet, dividends and analyst targets. Scaling is \
                    not uniform — margins, growth rates and returns are fractions (0.3934 means 39.34%), \
                    while dividendYield, fiveYearAvgDividendYield and debtToEquity are already percentages. \
                    A missing statistic is absent, never zero.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Ticker statistics",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getTicker(
            @McpToolParam(description = "Ticker symbol, e.g. AAPL or VOD.L", required = true)
            String ticker) {
        return api.get("/api/v1/ai/ticker/{ticker}", ticker);
    }

    @McpTool(name = "get_ticker_historical",
            description = """
                    Dividend, split and share-count history for one symbol, plus the growth figures derived \
                    from it: per-year dividend totals, year-over-year change, the current growth streak, the \
                    most recent raise and the cumulative split factor. Years marked partial are in progress \
                    or short of the usual payment cadence and are excluded from growth — do not read one as \
                    a dividend cut. The share-count series is sparse: a point exists only where the count \
                    changed, so carry the last value forward.""",
            annotations = @McpTool.McpAnnotations(
                    title = "Ticker history",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getTickerHistorical(
            @McpToolParam(description = "Ticker symbol, e.g. KO", required = true) String ticker,
            @McpToolParam(description = "Keep only the last N years of events. Omit for the full history.",
                    required = false) Integer years) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("years", years);
        return api.get("/api/v1/ai/ticker/{ticker}/historical", query, ticker);
    }

    @McpTool(name = "get_ticker_fundamentals",
            description = """
                    Financials one company actually reported to the SEC (revenue, net income, assets, \
                    liabilities, equity, diluted EPS, cash, debt, R&D, buybacks, dividend per share), \
                    newest first, each entry naming the filing it came from. US SEC registrants only — \
                    foreign issuers and crypto have none and return an error saying so.""",
            annotations = @McpTool.McpAnnotations(
                    title = "SEC fundamentals",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String getTickerFundamentals(
            @McpToolParam(description = "Ticker symbol, e.g. MSFT", required = true) String ticker,
            @McpToolParam(description = "Entries kept per concept, newest first. Default 12.",
                    required = false) Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", limit);
        return api.get("/api/v1/ai/ticker/{ticker}/fundamentals", query, ticker);
    }
}
