# REST API (Server / Data Center v1) & tooling

Default target: **Confluence Server / Data Center 7.x** (2022-era LTS 7.13 / 7.19). Base path is
`/rest/api/...`. The body field that holds storage format is `body.storage` with
`representation: "storage"`.

> Cloud is different: base `/wiki/rest/api` (v1, deprecated-ish) or `/wiki/api/v2/pages` (v2,
> current). v2 puts body at top level: `{"body": {"representation": "storage", "value": "…"}}`.
> Use v2 only when the user is on Cloud. Everything below is Server/DC v1.

## Auth

- **Personal Access Token (PAT)** — preferred on DC 7.9+: `Authorization: Bearer <token>`. Created
  by the user under their profile → Personal Access Tokens. Don't create or enter tokens for the
  user; ask them to generate one and supply it via env var.
- **Basic** — `-u user:password` (or an app password). Fine for scripts; avoid embedding creds.

Never hardcode credentials; read from environment (`CONFLUENCE_TOKEN`, etc.).

## Read a page (with storage body)

```bash
curl -H "Authorization: Bearer $CONFLUENCE_TOKEN" \
  "$BASE/rest/api/content/12345?expand=body.storage,version,space"
```
Response (trimmed): `body.storage.value` = the storage XML, `version.number` = current version,
`space.key`, `title`, `id`. Without `expand` you get only a basic representation; expansions limit
round-trips — combine them comma-separated (`expand=body.storage,version,ancestors`).

Find by space/title:
```bash
curl -H "Authorization: Bearer $CONFLUENCE_TOKEN" \
  "$BASE/rest/api/content?type=page&spaceKey=DOC&title=My%20Page&expand=body.storage,version"
```

## Create a page

```bash
curl -X POST -H "Authorization: Bearer $CONFLUENCE_TOKEN" -H "Content-Type: application/json" \
  "$BASE/rest/api/content" -d '{
    "type": "page",
    "title": "New Page",
    "space": {"key": "DOC"},
    "ancestors": [{"id": 12345}],
    "body": {"storage": {"value": "<p>Hello</p>", "representation": "storage"}}
  }'
```
`ancestors` is optional (sets the parent). Title must be unique within the space.

## Update a page (round-trip)

The #1 rule: **increment `version.number` by exactly 1** from the value you GET, or you get
409/400.
```bash
# 1) GET current body + version  → extract body.storage.value and version.number (= N)
# 2) modify the storage string
# 3) PUT with version N+1:
curl -X PUT -H "Authorization: Bearer $CONFLUENCE_TOKEN" -H "Content-Type: application/json" \
  "$BASE/rest/api/content/12345" -d '{
    "id": "12345",
    "type": "page",
    "title": "New Page",
    "space": {"key": "DOC"},
    "body": {"storage": {"value": "<p>Updated</p>", "representation": "storage"}},
    "version": {"number": <N+1>}
  }'
```
`title` and `type` are required on update even if unchanged. Add `version.message` for an audit
note. Optimistic locking means concurrent edits to the same version will conflict — re-GET and
retry.

## Delete

`DELETE $BASE/rest/api/content/12345` (trashes the page; add `?status=trashed` semantics vary by
version — verify on the target instance).

## Attachments

Create/update needs the `X-Atlassian-Token: no-check` header (XSRF) and multipart:
```bash
curl -X POST -H "Authorization: Bearer $CONFLUENCE_TOKEN" -H "X-Atlassian-Token: no-check" \
  -F "file=@diagram.png" -F "comment=auto-upload" \
  "$BASE/rest/api/content/12345/child/attachment"
```
Reference it from the body with `<ac:image><ri:attachment ri:filename="diagram.png"/></ac:image>`.
Re-uploading the same filename creates a new version of the attachment.

## Search with CQL

```bash
curl -H "Authorization: Bearer $CONFLUENCE_TOKEN" \
  "$BASE/rest/api/content/search?cql=space%3DDOC%20and%20type%3Dpage%20and%20text~%22kafka%22&expand=body.storage"
```
CQL fields: `space`, `type`, `title`, `text`, `label`, `created`, `lastmodified`, `ancestor`.
Useful for bulk operations ("every page with macro X", "all children of page Y"). Paginate with
`limit` / `start` (or `start` cursors on newer versions).

## Python: atlassian-python-api (convenient client)

```python
from atlassian import Confluence

conf = Confluence(url=BASE, token=TOKEN)                 # PAT; or username=/password=
page = conf.get_page_by_id(12345, expand="body.storage,version")
html = page["body"]["storage"]["value"]

conf.update_page(page_id=12345, title="New Page",
                 body="<p>Updated</p>", representation="storage")   # handles version bump
conf.create_page(space="DOC", title="Child", body="<p>hi</p>",
                 parent_id=12345, representation="storage")
conf.attach_file("diagram.png", page_id=12345)
```
The library handles version increment and the XSRF header for attachments. Good default for
scripting against Server/DC.

## Tooling for bulk / docs-as-code

- **confluence-publisher** (Asciidoctor → storage format, publishes a tree of pages, uploads
  attachments; Maven/Gradle/Docker, CI-friendly). Best path for AsciiDoc sources — point it at a
  space + ancestor and it manages create/update/version. Oleg has investigated this before.
- **sphinxcontrib-confluencebuilder** (Sphinx/reStructuredText → Confluence) for RST docs.
- **md-to-confluence / markdown-confluence** style tools for Markdown trees.
- For one-off conversions, generate storage with `generating.md` and push via the REST calls above.

## Failure cheatsheet

| Symptom | Cause / fix |
|---|---|
| 409 / "version must be incremented" | Didn't bump `version.number` (or someone edited concurrently) — re-GET, +1, retry |
| 400 "Error parsing xhtml" | Malformed storage XML — validate with the parser from `parsing.md`; check escaping/CDATA |
| 403 on attachment POST | Missing `X-Atlassian-Token: no-check` header |
| 404 on create | Wrong `space.key` or missing space permission |
| Macro renders as "Unknown macro" | Macro/app not installed on that instance, or wrong `ac:name`/params — copy from a real page's storage |
| Entities break your local parsing (not the API) | See `parsing.md` (declare entities / recover mode) — the API itself accepts `&nbsp;` etc. in storage |
