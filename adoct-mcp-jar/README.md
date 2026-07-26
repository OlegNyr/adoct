# adoct-mcp-jar

A self-contained, runnable **JAR** of the AsciiDocTools MCP server — start it with a single command,
`java -jar adoct-mcp.jar`, without installing the IDE plugin or building a native image.

Unlike the native binary (`:adoct-mcp-cli`), the fat JAR ships the **full** toolset, including
`confluence_publish_adoc` (publishing `.adoc` to Confluence, which needs asciidoctorj/JRuby).

> Русская версия — [README.RUS.md](README.RUS.md).

## Build

```bash
./gradlew :adoct-mcp-jar:shadowJar
# → the JAR is here:
#   adoct-mcp-jar/build/libs/adoct-mcp-<version>.jar
```

Requires **JDK 21+** to run.

## Run

```bash
# stdio (default) — for an MCP client
java -jar adoct-mcp.jar

# HTTP — server on http://127.0.0.1:7337/mcp
java -jar adoct-mcp.jar --http
# port/host: --port 7337  --bind 127.0.0.1
```

### HTTP mode

`--http` (or `MCP_TRANSPORT=http`, or `"transport": "http"` in the config) serves one endpoint,
`POST /mcp` (Streamable HTTP, `application/json`):

```bash
# with a single endpoint from the environment
MCP_HOST=https://confluence.example.com MCP_TOKEN=<PAT> \
  java -jar adoct-mcp.jar --http --port 7337 --bind 127.0.0.1

# or a full config file
java -jar adoct-mcp.jar --http --config adoct-mcp.json
```

- Point an HTTP-capable MCP client at `http://<bind>:<port>/mcp`. The `initialize` response carries an
  `Mcp-Session-Id` header.
- Concurrency: a fixed pool of **4** threads (a 5th request queues). stdio, by contrast, is strictly
  sequential.
- **Security:** the `/mcp` endpoint is unauthenticated and your Atlassian tokens live behind it. The
  default `--bind 127.0.0.1` keeps it on localhost; if you bind `0.0.0.0`, front it with a reverse
  proxy/firewall.
- The IntelliJ plugin can export a ready-to-use config: **Settings → Tools → AsciiDocTools → MCP
  server → Export config → JSON** (choose real tokens or `${ENV}` placeholders).

## Configuration (JSON)

Full configuration is a JSON file; its path is passed via `--config <path>` or the `MCP_CONFIG`
environment variable. Format:

```json
{
  "transport": "stdio",
  "defaultJiraProject": "ABC",
  "defaultConfluenceSpace": "DOC",
  "endpoints": [
    { "host": "https://jira.example.com",       "kind": "jira",       "token": "${JIRA_TOKEN}",       "default": true },
    { "host": "https://confluence.example.com", "kind": "confluence", "token": "${CONFLUENCE_TOKEN}" },
    { "host": "https://bitbucket.example.com",  "kind": "bitbucket",  "token": "${BITBUCKET_TOKEN}" }
  ],
  "team": [
    { "username": "ivanov", "displayName": "Ivan Ivanov",    "role": "Backend" },
    { "username": "petrov", "displayName": "Petr Petrov",    "role": "QA" },
    { "username": "sidorova", "displayName": "Anna Sidorova", "role": "Analyst" }
  ]
}
```

- `kind` — `jira` | `confluence` | `bitbucket`. May be omitted: the type is detected from the host
  (`jira`/`bitbucket`/`stash` in the address), otherwise `confluence`.
- `default: true` — the primary host of its kind (calls without an explicit `host` go there).
- **`token` and `host` support `${ENV_VAR}`** — the value is taken from an environment variable instead
  of being stored in the file. An undefined variable expands to an empty string.

### Team (roster)

The `team` array (see the config example above) is the list of team members; MCP tools use it to map
assignees and roles (who to assign issues to, who is on the team). Element fields:

- `username` — the Jira/Confluence login (required; blank entries are skipped).
- `displayName` — the display name.
- `role` — the team role (e.g. `Backend`, `QA`, `Analyst`).

### Tokens from environment variables

This keeps secrets out of the JSON. The MCP client launches the process and passes tokens via the `env`
block, while the config references them with `${...}`:

```json
{
  "mcpServers": {
    "adoct": {
      "command": "java",
      "args": ["-jar", "/absolute/path/adoct-mcp.jar", "--config", "/absolute/path/adoct-mcp.json"],
      "env": {
        "JIRA_TOKEN": "first-PAT",
        "CONFLUENCE_TOKEN": "second-PAT",
        "BITBUCKET_TOKEN": "third-PAT"
      }
    }
  }
}
```

(`mcpServers` is the MCP-client config format, e.g. Claude Desktop's `claude_desktop_config.json`.)

### Quick mode — a single host entirely from the environment

Without a JSON file: a single endpoint is defined by environment variables (overrides `endpoints`):

```json
{
  "mcpServers": {
    "adoct": {
      "command": "java",
      "args": ["-jar", "/absolute/path/adoct-mcp.jar"],
      "env": {
        "MCP_HOST": "https://jira.example.com",
        "MCP_TOKEN": "your-PAT",
        "MCP_KIND": "jira"
      }
    }
  }
}
```

## Environment variables

| Variable | Purpose |
|---|---|
| `MCP_CONFIG` | path to the JSON config (same as `--config`) |
| `MCP_HOST` / `MCP_TOKEN` / `MCP_KIND` | quick single endpoint (overrides `endpoints`) |
| `MCP_TRANSPORT` | `stdio` (default) or `http` |
| `MCP_PORT` / `MCP_BIND` | port/address for HTTP mode (default `7337` / `127.0.0.1`) |
| `MCP_JIRA_PROJECT` / `MCP_CONFLUENCE_SPACE` | default project/space |

Plus any `${VAR}` referenced by the JSON config (e.g. `JIRA_TOKEN` above).

Source precedence (ascending): JSON file → `MCP_*` environment variables → command-line arguments.
