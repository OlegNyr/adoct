# Generating the storage format

Goal: produce a body string that Confluence accepts as `representation: "storage"`. Two failure
modes dominate: **unescaped text** (a stray `&`, `<`, or `>` breaks the XML and the REST API
rejects it or the page corrupts) and **malformed macros**. Build with small helpers, not raw
string concatenation, and validate that the result parses before sending.

## Escaping rules (non-negotiable)

- In **text nodes and attribute values**: escape `&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`; in
  attributes also `"`→`&quot;`. Do this for ALL user/dynamic content.
- In **code / plain-text bodies**: wrap in `<![CDATA[ … ]]>` instead of escaping — preserves
  formatting and special characters. Caveat: CDATA can't contain the literal sequence `]]>`; if the
  content might, split it: `]]]]><![CDATA[>`.
- Self-close void elements: `<br/>`, `<hr/>`, `<img …/>`, `<ac:emoticon …/>`.
- Prefer numeric entities (`&#160;`) over named ones (`&nbsp;`) when you can't guarantee a DTD is
  present downstream — numeric refs are always valid XML.

## Minimal Python helpers

```python
from xml.sax.saxutils import escape, quoteattr

def text(s: str) -> str:                 # safe text node
    return escape(s, {})                  # escapes & < >

def attr(s: str) -> str:                 # safe attribute value (includes quotes)
    return quoteattr(s)                   # returns a quoted string

def cdata(s: str) -> str:
    return "<![CDATA[" + s.replace("]]>", "]]]]><![CDATA[>") + "]]>"

def code_macro(src: str, language: str = "text", title: str | None = None) -> str:
    params = f'<ac:parameter ac:name="language">{text(language)}</ac:parameter>'
    if title:
        params += f'<ac:parameter ac:name="title">{text(title)}</ac:parameter>'
    return (f'<ac:structured-macro ac:name="code">{params}'
            f'<ac:plain-text-body>{cdata(src)}</ac:plain-text-body></ac:structured-macro>')

def admonition(kind: str, body_html: str, title: str | None = None) -> str:
    # kind in {info, tip, note, warning}
    t = f'<ac:parameter ac:name="title">{text(title)}</ac:parameter>' if title else ""
    return (f'<ac:structured-macro ac:name="{kind}">{t}'
            f'<ac:rich-text-body>{body_html}</ac:rich-text-body></ac:structured-macro>')

def page_link(title: str, label: str, space: str | None = None) -> str:
    sk = f' ri:space-key={attr(space)}' if space else ""
    return (f'<ac:link><ri:page ri:content-title={attr(title)}{sk}/>'
            f'<ac:plain-text-link-body>{cdata(label)}</ac:plain-text-link-body></ac:link>')

def toc(max_level: int = 3) -> str:
    return (f'<ac:structured-macro ac:name="toc">'
            f'<ac:parameter ac:name="maxLevel">{max_level}</ac:parameter></ac:structured-macro>')
```
Note: in `body_html` passed to `admonition`, any *text* must already be escaped — only feed it
markup you generated through these helpers or known-safe constants.

## Building tables from data

```python
def table(headers, rows):
    head = "".join(f"<th>{text(h)}</th>" for h in headers)
    body = "".join("<tr>" + "".join(f"<td>{text(str(c))}</td>" for c in r) + "</tr>" for r in rows)
    return f"<table><tbody><tr>{head}</tr>{body}</tbody></table>"
```

## Two-column layout scaffold

```python
def two_column(left: str, right: str) -> str:
    return ('<ac:layout><ac:layout-section ac:type="two_equal">'
            f'<ac:layout-cell>{left}</ac:layout-cell>'
            f'<ac:layout-cell>{right}</ac:layout-cell>'
            '</ac:layout-section></ac:layout>')
```
Remember: if you use `ac:layout`, it must be the top-level element of the whole body.

## Validate before sending

Run the generated body through the parser from `parsing.md` (wrap + entities + namespaces). If it
parses, the REST API will accept the markup structurally. Cheap guard that catches most mistakes:
```python
parse_storage(generated_body)   # raises on malformed XML
```

## Converting from other formats

- **Markdown → storage:** convert MD → HTML (e.g. `markdown`, `mistune`), then map blocks to
  storage: fenced code → `code_macro`, blockquote-callouts → admonitions, images → `ac:image`
  with `ri:attachment`/`ri:url`, internal links → `ac:link`. Sanitize the intermediate HTML
  (strip script/style, ensure void elements are self-closed) so it's valid XML.
- **AsciiDoc → storage:** prefer the **confluence-publisher** toolchain (Asciidoctor →
  storage-format, handles admonitions/code/TOC and attachment upload) rather than rolling your
  own — see `rest-api.md`. Oleg has used this path before for OTP docs.
- **HTML → storage:** most HTML is already close; the work is (1) make it well-formed XML
  (self-close voids, close tags, escape stray `&`), (2) replace `<pre><code>` with the code macro
  if you want syntax highlighting, (3) rewrite `<img src>` to `ac:image`. Don't ship raw `<script>`
  / `<style>` — they won't render and may be stripped.

## Gotchas

- Don't emit `ac:macro-id`; let Confluence assign it. Reusing the same id across pages can confuse
  the editor.
- The `html` macro (embedding raw HTML) is frequently disabled on Server/DC — don't rely on it.
- Empty rich-text bodies still need the tags: `<ac:rich-text-body></ac:rich-text-body>`.
- Confluence may normalize/reformat your markup on save (round-tripping isn't byte-identical) —
  compare semantically, not by string equality.
