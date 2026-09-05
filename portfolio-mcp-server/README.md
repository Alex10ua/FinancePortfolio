# portfolio-mcp-server

Read-only MCP server over FinancePortfolio. It logs in to the Spring backend and
exposes its `/api/v1/ai/**` endpoints as 17 tools.

No write path exists: the HTTP client class offers only `get(...)`, every backend
endpoint it calls is a `@GetMapping`, and each tool declares `readOnlyHint`. The
one non-GET request is the login POST.

## Two ways to run it

One image, transport chosen by Spring profile:

| Mode | Profile | How it runs | Client config |
|---|---|---|---|
| **HTTP** (default in compose) | `http` | Long-lived `portfolio-mcp` service on `127.0.0.1:8081` | `{"type":"http","url":"http://localhost:8081/mcp"}` |
| **stdio** | none | Client execs a JVM per session inside the `portfolio-mcp-stdio` service | `command: docker`, see below |

HTTP is the simpler path — start the stack and it is there. stdio suits a client
on a machine that cannot reach the port, or when you would rather not have
anything listening at all.

Both are compose services now. `portfolio-mcp-stdio` runs no server of its own —
its command is an idle `tail -f /dev/null`, and the client starts its own JVM in
it with `docker exec -i` per session. A stdio server must own the *client's*
stdin, which a compose-managed process cannot be handed, so the container is a
stable target rather than the server itself: one named container instead of a
throwaway `docker run` container per session, one place that owns the network
attachment, and credentials that can live in `.env` like every other service.

> The HTTP endpoint has **no authentication of its own**. The compose mapping
> publishes it on `127.0.0.1` only, so it is not reachable from the LAN. Do not
> widen that to `0.0.0.0` without putting auth in front of it — anyone who
> reaches the port gets read access to every portfolio the configured account owns.

---

## Prerequisites

- The backend running and reachable (`docker compose up -d portfolio-backend`, or
  the full stack).
- A portfolio account — the same username and password used to sign in at
  `http://localhost:3001`. For the compose service these go in the root `.env` as
  `PORTFOLIO_USERNAME` / `PORTFOLIO_PASSWORD`.

## Run it over HTTP (compose)

```bash
docker compose up -d --build portfolio-mcp     # starts mongodb + backend too
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/mcp   # 405/406 = listening
```

Then point the client at it:

```json
{
  "mcpServers": {
    "portfolio": { "type": "http", "url": "http://localhost:8081/mcp" }
  }
}
```

No credentials in the client config — the server holds them, from `.env`.

## Run it over stdio (compose)

```bash
docker compose up -d --build portfolio-mcp-stdio    # idle holder container + backend
docker ps --filter name=portfolio-mcp-stdio         # command reads "tail -f /dev/null"
```

Nothing is listening and no JVM is running yet — the client starts one per
session. Smoke-test the exec path by hand:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}' \
  | docker exec -i portfolio-mcp-stdio java -jar app.jar 2>/dev/null
```

A `serverInfo` line on stdout means the transport is fine.

## Build only

The repo has no host JDK, so build the image:

```bash
docker compose build portfolio-mcp
```

Both MCP services share `portfolio-mcp-image:latest`, so either name builds it.

Or, with a JDK 25 available, a plain jar:

```bash
cd portfolio-mcp-server
mvn clean package
```

## Register with a client

### Docker (no host JDK needed)

`.mcp.json` in the repo root, or the Claude Desktop config:

```json
{
  "mcpServers": {
    "portfolio": {
      "command": "docker",
      "args": [
        "exec", "-i",
        "-e", "PORTFOLIO_USERNAME",
        "-e", "PORTFOLIO_PASSWORD",
        "portfolio-mcp-stdio",
        "java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0",
        "-jar", "app.jar"
      ],
      "env": {
        "PORTFOLIO_USERNAME": "your-username",
        "PORTFOLIO_PASSWORD": "your-password"
      }
    }
  }
}
```

Three things that look optional and are not:

- `-i` — without it the JVM gets no stdin and the client sees the server die
  immediately.
- `app.jar`, **relative**. The image's WORKDIR is `/app` and `docker exec` starts
  there, so the relative name resolves. Write `/app/app.jar` and it still works
  from a client, but breaks the moment you paste the command into Git Bash on
  Windows, which rewrites a leading `/` into `C:/Program Files/Git/...` and the
  JVM reports `Unable to access jarfile`.
- No `--network` and no `PORTFOLIO_API_BASE_URL`. The container is already on
  `portfolio-network` and already knows the backend URL, both from compose.

The two `-e` flags forward the values from the client's own environment, and
exec-level env wins over the container's. Drop them (and the `env` block) once
`PORTFOLIO_USERNAME` / `PORTFOLIO_PASSWORD` are set in the root `.env` — then no
credentials sit in any client config, same as the HTTP mode. Note the shipped
`.env` has both keys **present but empty**, which is not the same as set: with
empty values the login fails, so either fill them in or keep the `-e` flags.

Old `docker run -i --rm … portfolio-mcp-image:latest` registrations still work,
they just leave a randomly-named container per session; switch them to the exec
form and delete the strays with `docker rm -f <name>`.

### Host JVM

```json
{
  "mcpServers": {
    "portfolio": {
      "command": "java",
      "args": ["-jar", "C:/MyProjects/FinancePortfolio/portfolio-mcp-server/target/portfolio-mcp-server-0.0.1-SNAPSHOT.jar"],
      "env": {
        "PORTFOLIO_API_BASE_URL": "http://localhost:8080",
        "PORTFOLIO_USERNAME": "your-username",
        "PORTFOLIO_PASSWORD": "your-password"
      }
    }
  }
}
```

## Registering it from mcp.json

`mcp.json` in the repo root is the single source of truth. `register-mcp.mjs` fans it out
to Claude Code and Codex, which store MCP config differently (JSON vs TOML):

```bash
node register-mcp.mjs --list          # what is registered today
node register-mcp.mjs --list       # show the changes, write nothing
node register-mcp.mjs --scope local   # register for this project only
node register-mcp.mjs --target claude --force
```

| Flag | Meaning |
|---|---|
| `--file <path>` | Source config (default `mcp.json`, then `.mcp.json`) |
| `--target claude \| codex \| both` | Which client to write to (default both) |
| `--scope user \| project \| local` | Claude scope: `~/.claude.json` top level, `.mcp.json`, or that file's per-project entry (default `user`) |
| `--name <server>` | Register only this server; repeatable |
| `--force` | Replace an entry that already exists |
| `--dry-run`, `--list` | Inspect without writing |
| `--resolve-env` | Expand `${VAR}` for Claude too |

It edits the config files directly rather than shelling out to `claude mcp` / `codex mcp`,
because those CLIs ship inside the editor extensions and are usually not on PATH. Every
file it touches is backed up first (`.bak-<timestamp>`) and unrelated entries are kept.

**Credentials.** Prefer `${PORTFOLIO_USERNAME}` / `${PORTFOLIO_PASSWORD}` in `mcp.json`
over literal values, and export them in your shell. Claude Code expands `${VAR}` itself so
it is stored as written; Codex cannot, so the script resolves values at registration time —
meaning **the password lands in plain text in `~/.codex/config.toml`**. The script warns
when a value is still a placeholder such as `test`, or when a variable resolves to nothing.

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `PORTFOLIO_API_BASE_URL` | `http://localhost:8080` | Backend root, no trailing slash |
| `PORTFOLIO_USERNAME` | — | Portfolio account; required |
| `PORTFOLIO_PASSWORD` | — | Portfolio account; required |
| `PORTFOLIO_API_TIMEOUT_SECONDS` | `30` | Per-request read timeout |

Logs go to **stderr** (`logback-spring.xml`), which MCP leaves free and clients
capture into their own logs. Nothing may write to stdout — that carries the
protocol.

## Tools

| Tool | Answers |
|---|---|
| `list_portfolios` | Which portfolios exist, and in what currencies |
| `get_portfolio_snapshot` | Whole portfolio: positions, cash, totals, realized P&L |
| `get_positions` | Holdings only |
| `get_diversification` | Value by country / sector / industry / ticker |
| `get_tags` | Holdings grouped by the user's own tags |
| `get_transactions` | Filtered, paged history |
| `get_performance` | Invested, value, P&L, XIRR, value series |
| `get_portfolio_history` | Month-end value over time, per currency |
| `get_realized_pnl` | FIFO realized P&L per currency |
| `get_dividends` | Income received, monthly / quarterly / yearly |
| `get_dividend_calendar` | Expected income by calendar month |
| `get_watchlist` | Watched tickers with yield percentile ranking |
| `get_ticker` | Price, classification, ~75 key statistics |
| `get_ticker_historical` | Dividend / split / share-count history plus growth |
| `get_ticker_fundamentals` | SEC EDGAR reported financials |
| `get_fx_rates` | Currency → units per 1 EUR |
| `get_conventions` | How to read every response (also an MCP resource) |

## The currency contract

The backend answers in each asset's **native currency** and never converts — that
is a deliberate repo-wide rule, not an oversight. Every response therefore carries
what is needed to convert:

```
amount_in_TARGET = amount * fxRates[TARGET] / fxRates[SOURCE]
```

`fxRates` is quoted against the euro (so `EUR` is `1.0`) and includes synthetic
`GBp`/`GBx` entries equal to the GBP rate × 100, because London listings are
quoted in pence. That means the one formula covers pence with no special case.

Totals in the snapshot are pre-grouped by currency, so converting a whole
portfolio is one multiply per currency rather than one per position.

## Troubleshooting

**Client reports a JSON parse error, or the server "exits immediately".**
Something wrote to stdout, which carries the JSON-RPC frames. Check stderr, and
verify `logback-spring.xml` still has no stdout appender and that
`application.properties` still sets `spring.main.banner-mode=off` and
`spring.main.web-application-type=none`.

**"No backend credentials configured".** `PORTFOLIO_USERNAME` /
`PORTFOLIO_PASSWORD` were not passed through. With the Docker form, `-e VAR`
without a value forwards it from the `env` block — both the `args` entry and the
`env` entry must be present.

**"Login … failed with HTTP …". Is the backend running?**
`curl -i -X POST http://localhost:8080/login -d 'username=…&password=…'` should
answer `200`.

**Tools are listed but every call 403s.** The account owns no portfolio with that
id. Call `list_portfolios` first and use an id from it.

**A tool returns "no data for …/ticker/X".** No provider has fetched that symbol
yet. This server never triggers a fetch — refresh it from the app or the Flask
admin console at `http://localhost:5000/admin`.
