package com.dev.alex.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Read-only MCP server over FinancePortfolio.
 * <p>
 * Runs as a stdio subprocess spawned by an MCP client (Claude Code, Claude
 * Desktop), not as a long-lived service: it speaks JSON-RPC on stdin/stdout and
 * calls the Spring backend's /api/v1/ai/** endpoints over HTTP.
 * <p>
 * Every tool is a GET. The client class exposes no write method, so there is no
 * code path here that could modify a portfolio.
 */
@SpringBootApplication
public class PortfolioMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioMcpApplication.class, args);
    }
}
