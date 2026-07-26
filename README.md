# AsciiDocTools

[![publish-plugin](https://github.com/OlegNyr/adoct/actions/workflows/publish.yaml/badge.svg)](https://github.com/OlegNyr/adoct/actions/workflows/publish.yaml)

**Treat your Confluence documentation as code.** AsciiDocTools is a toolkit for moving content
between **Confluence** and **AsciiDoc** in both directions — as an **IntelliJ IDEA plugin**, a
**Maven plugin** for CI pipelines, and an embedded **MCP server** that exposes Jira / Confluence /
Bitbucket to AI agents.

<!-- Plugin description -->
**AsciiDocTools** converts Confluence pages into [AsciiDoc](https://asciidoc.org/) and publishes
AsciiDoc back to Confluence, so you can keep technical documentation in your repository, review it
in pull requests, and round-trip it to a Confluence space.

Key capabilities:

- **Export Confluence → AsciiDoc.** Pull a page (and its whole child-page subtree) live via the
  Confluence REST API, or convert an offline HTML "storage" export. Attachments and images are
  downloaded, internal links are resolved, and Confluence macros are translated
  (code, PlantUML, draw.io, expand, note/info/tip/warning, Jira, table of contents, tabs, panel,
  profile, status, include / excerpt-include, and more).
- **Publish AsciiDoc → Confluence.** A folder of `.adoc` files becomes a Confluence page tree
  (`index.adoc` = the node page, other files = its children). Pages round-trip through a
  `:confluency-id:` attribute, images become attachments, `:keywords:` become labels, and only
  changed content is re-uploaded.
- **Embedded MCP server.** A local server exposes `jira_*`, `confluence_*` and `bitbucket_*` tools
  (issues and links, page and code search, repositories / files / pull requests) so AI assistants
  can work against your Atlassian stack.
- **Jira & Bitbucket integration** for Confluence Server / Data Center, with multi-host routing by
  service type.

Import a file with **Alt+I**, export a Confluence page with **Alt+Shift+I**, and configure servers
under *Settings → Tools → AsciiDocTools*.
<!-- Plugin description end -->

## Features in detail

### Confluence → AsciiDoc (export)
- Live pull via the Confluence REST API, recursively exporting a page and its child-page subtree
  (each child lands in its own subfolder).
- Offline conversion of an exported HTML "storage" document.
- Downloads attachments/images, resolves internal Confluence links (page, user, attachment).
- Translates Confluence macros to AsciiDoc, preserves colors and styles.
- Splits large documents (> 700 lines) into multiple files on level‑2 headings.

### AsciiDoc → Confluence (publish / round-trip)
- Publishes a single `.adoc` or a whole folder tree, mirroring the folder structure as a Confluence
  page hierarchy.
- Stores the target page id in `:confluency-id:` for lossless round-trips; `:confluency-id: ignore`
  skips a file.
- Uploads referenced images/files as attachments; maps `:keywords:` to page labels.
- Content-hash change detection — unchanged pages and attachments are not re-uploaded.

### Maven plugin — publish from CI
`io.github.olegnyr:adoct-maven-plugin` publishes a folder (or a single `.adoc`) to Confluence from
Maven, reusing the same engine:

```bash
mvn io.github.olegnyr:adoct-maven-plugin:1.0.0:publish \
    -Dconfluence.pageUrl=https://confluence.example.com/pages/viewpage.action?pageId=12345 \
    -Dconfluence.token=$CONFLUENCE_PAT
```

See [`adoct-maven-plugin/README.md`](adoct-maven-plugin/README.md) for all parameters.

### MCP server — Atlassian for AI agents
An embedded [Model Context Protocol](https://modelcontextprotocol.io/) server exposing Jira,
Confluence and Bitbucket tools. It runs inside the IDE plugin, as a standalone CLI, or as a GraalVM
native image.

Run the standalone server (fat JAR from the release, or `./gradlew :adoct-mcp-jar:shadowJar`):

```bash
# stdio (default) — a desktop MCP client spawns this once per session
java -jar adoct-mcp.jar

# HTTP (Streamable HTTP) — listens on http://127.0.0.1:7337/mcp
java -jar adoct-mcp.jar --http
#   --port 7337   --bind 127.0.0.1      (or env MCP_PORT / MCP_BIND / MCP_TRANSPORT=http)
```

Point an HTTP-capable MCP client at `http://<bind>:<port>/mcp`. Requests run on a small thread pool
(up to 4 concurrent); `initialize` returns an `Mcp-Session-Id` header. The default bind is
`127.0.0.1` (localhost only) — if you expose it with `--bind 0.0.0.0`, put it behind a reverse
proxy/firewall, since the `/mcp` endpoint itself is unauthenticated and carries your Atlassian tokens.

Endpoints (host + token per Jira/Confluence/Bitbucket) come from a JSON config (`--config` /
`MCP_CONFIG`) or quick env vars (`MCP_HOST` / `MCP_TOKEN` / `MCP_KIND`) — see
[`adoct-mcp-jar/README.md`](adoct-mcp-jar/README.md). The IntelliJ plugin can generate that JSON for
you: **Settings → Tools → AsciiDocTools → MCP server → Export config → JSON**.

## Install the IntelliJ plugin

The plugin is distributed through a custom plugin repository on GitHub Pages (not the JetBrains
Marketplace):

1. **Settings → Plugins → ⚙ → Manage Plugin Repositories → +**
2. Add the URL:
   ```
   https://olegnyr.github.io/adoct/updatePlugins.xml
   ```
3. Install **AsciiDocTools** from the Marketplace tab. Updates arrive automatically (scheduled check
   or **Check for updates**).

## Modules

A Gradle multi-module build (JVM toolchain 21):

| Module | What it is |
|---|---|
| `adoct-confluence` | The conversion engine (`parser` = Confluence→AsciiDoc, `generate` = AsciiDoc→Confluence). Pure Java library. |
| `adoct-jira` | Jira Server/DC client. |
| `adoct-bitbucket` | Bitbucket Server/DC client (code search + repos/files/PRs, read-only). |
| `adoct-anonymize` | Export anonymizer + bug-report bundling. |
| `adoct-mcp` / `adoct-mcp-cli` | Embedded MCP server and its standalone/native CLI. |
| `adoct-maven-plugin` | Maven plugin (`adoct:publish`) that publishes to Confluence. |
| `adoct-idea` | The IntelliJ IDEA plugin that wires it all together. |

## Build & run

```bash
./gradlew :adoct-idea:runIde        # launch a sandbox IDE with the plugin
./gradlew :adoct-idea:buildPlugin   # assemble the plugin zip
./gradlew build                     # compile + test + assemble every module
```

## Releasing

CI (`.github/workflows/publish.yaml`) builds and publishes everything on a `vX.Y.Z` tag push:

1. Bump `pluginVersion` in `gradle.properties` (strictly increasing).
2. Commit, then tag with the matching version and push:
   ```bash
   git tag v$(grep '^pluginVersion' gradle.properties | cut -d= -f2 | xargs)
   git push github --tags
   ```
3. The workflow then:
   - builds the IntelliJ plugin (`-Drelease=true`), attaches the `.zip` to a **GitHub Release**, and
     deploys `.zip` + `updatePlugins.xml` to **GitHub Pages** (which the IDE polls);
   - publishes `adoct-confluence` and `adoct-maven-plugin` to **Maven Central**
     (`io.github.olegnyr`).

## License

[Apache License 2.0](LICENSE).
