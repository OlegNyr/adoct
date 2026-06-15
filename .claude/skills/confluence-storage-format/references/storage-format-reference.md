# Storage format reference (element & macro catalog)

The body is XHTML-based XML. Standard HTML block/inline tags work as-is; Confluence-specific
features use the `ac:` / `ri:` / `at:` namespaces. This catalogs what you'll actually emit and
encounter. Compatible across Confluence 7.x–10.x.

## Table of contents
1. Text & inline formatting
2. Breaks & rules
3. Lists & task lists
4. Links (ac:link) & anchors
5. Images (ac:image)
6. Tables
7. Page layouts (ac:layout)
8. Macros (ac:structured-macro) — structure & common ones
9. Resource identifiers (ri:*)
10. Emoticons
11. Templates (at:*) & placeholders

---

## 1. Text & inline formatting

Plain XHTML. Headings `<h1>`–`<h6>`. Paragraph `<p>`.
- bold `<strong>…</strong>`; italic `<em>…</em>`; underline `<u>…</u>`
- strikethrough `<span style="text-decoration: line-through;">…</span>`
- superscript `<sup>`, subscript `<sub>`, monospace `<code>`, preformatted `<pre>`
- block quote `<blockquote><p>…</p></blockquote>`
- color `<span style="color: rgb(255,0,0);">…</span>`; `<small>`, `<big>`
- align via `<p style="text-align: center;">` / `right`

## 2. Breaks & rules

- new paragraph: separate `<p>` elements
- line break: `<br/>` (self-closing — must be closed)
- horizontal rule: `<hr/>`
- em/en dash via entities `&mdash;` / `&ndash;` (see parsing.md re: entity handling)

## 3. Lists & task lists

Unordered `<ul><li>…</li></ul>`; ordered `<ol><li>…</li></ol>`; nest by putting a `<ul>`/`<ol>`
inside an `<li>`.

Task list (checkboxes):
```xml
<ac:task-list>
  <ac:task>
    <ac:task-status>incomplete</ac:task-status>   <!-- or "complete" -->
    <ac:task-body>task list item</ac:task-body>
  </ac:task>
</ac:task-list>
```

## 4. Links (ac:link) & anchors

Internal links use `<ac:link>` with a child `ri:*` target and an optional body. External links use
plain `<a href>`.

```xml
<!-- to another page -->
<ac:link>
  <ri:page ri:content-title="Page Title" ri:space-key="DOC"/>
  <ac:plain-text-link-body><![CDATA[Link text]]></ac:plain-text-link-body>
</ac:link>

<!-- to an attachment -->
<ac:link>
  <ri:attachment ri:filename="diagram.png"/>
  <ac:plain-text-link-body><![CDATA[the diagram]]></ac:plain-text-link-body>
</ac:link>

<!-- external -->
<a href="https://example.com">Example</a>

<!-- anchor on same page (created by the anchor macro, see below) -->
<ac:link ac:anchor="myAnchor">
  <ac:plain-text-link-body><![CDATA[jump]]></ac:plain-text-link-body>
</ac:link>

<!-- anchor on another page -->
<ac:link ac:anchor="myAnchor">
  <ri:page ri:content-title="Other Page"/>
  <ac:plain-text-link-body><![CDATA[jump]]></ac:plain-text-link-body>
</ac:link>
```
Body forms: `<ac:plain-text-link-body><![CDATA[…]]></ac:plain-text-link-body>` for text;
`<ac:link-body>…</ac:link-body>` for rich content (only `<b> <strong> <em> <i> <code> <tt> <sub>
<sup> <br> <span>` allowed inside, plus `<ac:image>`). No body → label auto-generated from the
target. A bare `<ac:link/>` links to the current page.

## 5. Images (ac:image)

```xml
<!-- attached image -->
<ac:image ac:height="250"><ri:attachment ri:filename="logo.png"/></ac:image>
<!-- external image -->
<ac:image><ri:url ri:value="https://example.com/a.png"/></ac:image>
```
Supported attributes (on `ac:image`): `ac:align`, `ac:border`, `ac:class`, `ac:title`, `ac:style`,
`ac:thumbnail`, `ac:alt`, `ac:height`, `ac:width`, `ac:vspace`, `ac:hspace`.

## 6. Tables

Plain XHTML tables. Header cells `<th>`, data cells `<td>`; `rowspan`/`colspan` supported.
```xml
<table><tbody>
  <tr><th>H1</th><th>H2</th></tr>
  <tr><td>a</td><td>b</td></tr>
  <tr><td rowspan="2">merged</td><td>c</td></tr>
  <tr><td>d</td></tr>
</tbody></table>
```

## 7. Page layouts (ac:layout)

Native multi-column layout. When present, `<ac:layout>` must be the **top-level** element of the
page body.
```xml
<ac:layout>
  <ac:layout-section ac:type="two_equal">
    <ac:layout-cell>{content}</ac:layout-cell>
    <ac:layout-cell>{content}</ac:layout-cell>
  </ac:layout-section>
</ac:layout>
```
`ac:layout-section` → a row; `ac:layout-cell` → a column. `ac:type` values and required cell count:
`single`(1), `two_equal`(2), `two_left_sidebar`(2), `two_right_sidebar`(2), `three_equal`(3),
`three_with_sidebars`(3).

## 8. Macros (ac:structured-macro)

General shape:
```xml
<ac:structured-macro ac:name="MACRO_NAME" ac:schema-version="1">
  <ac:parameter ac:name="paramName">value</ac:parameter>
  <!-- body: choose ONE form, or none -->
  <ac:rich-text-body><p>XHTML content</p></ac:rich-text-body>
  <!-- or -->
  <ac:plain-text-body><![CDATA[raw text]]></ac:plain-text-body>
</ac:structured-macro>
```
- `ac:name` — the macro key. `ac:macro-id` (a UUID) appears in content saved by the editor; it's
  optional when you author by hand and Confluence will assign one.
- Parameters with no name use `ac:name=""` (the "default" parameter).
- A parameter value can itself be a `ri:*` element (e.g. space/user references).
- Legacy form `<ac:macro ac:name="…">` with `<ac:default-parameter>` / `<ac:body>` may appear in
  old content; read it, but emit `<ac:structured-macro>`.

### Common macros (storage markup)

**Code block** (`code`) — body is plain-text CDATA:
```xml
<ac:structured-macro ac:name="code">
  <ac:parameter ac:name="language">java</ac:parameter>
  <ac:parameter ac:name="title">Example</ac:parameter>
  <ac:parameter ac:name="linenumbers">true</ac:parameter>
  <ac:plain-text-body><![CDATA[System.out.println("hi");]]></ac:plain-text-body>
</ac:structured-macro>
```

**Admonitions** (`info`, `tip`, `note`, `warning`) — rich-text body:
```xml
<ac:structured-macro ac:name="info">
  <ac:parameter ac:name="title">Heads up</ac:parameter>
  <ac:parameter ac:name="icon">true</ac:parameter>
  <ac:rich-text-body><p>Important <em>information</em>.</p></ac:rich-text-body>
</ac:structured-macro>
```

**Panel** (`panel`):
```xml
<ac:structured-macro ac:name="panel">
  <ac:parameter ac:name="title">My Panel</ac:parameter>
  <ac:parameter ac:name="borderStyle">solid</ac:parameter>
  <ac:parameter ac:name="bgColor">#E3FCEF</ac:parameter>
  <ac:rich-text-body><p>content</p></ac:rich-text-body>
</ac:structured-macro>
```

**Table of contents** (`toc`), no body:
```xml
<ac:structured-macro ac:name="toc">
  <ac:parameter ac:name="maxLevel">3</ac:parameter>
  <ac:parameter ac:name="minLevel">1</ac:parameter>
</ac:structured-macro>
```

**Expand** (`expand`):
```xml
<ac:structured-macro ac:name="expand">
  <ac:parameter ac:name="title">Show details</ac:parameter>
  <ac:rich-text-body><p>hidden content</p></ac:rich-text-body>
</ac:structured-macro>
```

**Status lozenge** (`status`), no body:
```xml
<ac:structured-macro ac:name="status">
  <ac:parameter ac:name="colour">Green</ac:parameter>   <!-- Grey/Red/Yellow/Green/Blue -->
  <ac:parameter ac:name="title">DONE</ac:parameter>
</ac:structured-macro>
```

**Anchor** (`anchor`) — default parameter is the anchor name, no body:
```xml
<ac:structured-macro ac:name="anchor"><ac:parameter ac:name="">myAnchor</ac:parameter></ac:structured-macro>
```

**Include another page** (`include`) — body holds an ac:link to the page:
```xml
<ac:structured-macro ac:name="include">
  <ac:parameter ac:name=""><ac:link><ri:page ri:content-title="Snippet" ri:space-key="DOC"/></ac:link></ac:parameter>
</ac:structured-macro>
```

**Excerpt / excerpt-include** (`excerpt`, `excerpt-include`), **children display** (`children`),
**page-properties** / **detailssummary**, **jira** (single issue / JQL), **drawio/gliffy**
(vendor) follow the same `ac:structured-macro` shape — inspect a real page's storage format
("View storage format") to copy exact parameter names, especially for third-party/vendor macros
which this catalog can't enumerate.

**HTML/embed caveat:** the `html` macro is often disabled by admins on Server/DC for security;
don't assume it's available.

## 9. Resource identifiers (ri:*)

References used inside `ac:link`, `ac:image`, macro parameters, attachments:
| Element | Key attributes |
|---|---|
| `<ri:page ri:content-title="…" ri:space-key="…"/>` | title required; space-key optional (omit → relative to current space) |
| `<ri:blog-post ri:content-title="…" ri:space-key="…" ri:posting-day="YYYY/MM/DD"/>` | title + posting-day required |
| `<ri:attachment ri:filename="…">` *(optional container ri:* inside)* | filename required; empty container → attachment on current page |
| `<ri:url ri:value="…"/>` | absolute URL |
| `<ri:shortcut ri:key="jira" ri:parameter="ABC-123"/>` | shortcut key + parameter |
| `<ri:user ri:userkey="…"/>` | Server/DC uses `ri:userkey`; Cloud uses `ri:account-id` |
| `<ri:space ri:space-key="…"/>` | space key |
| `<ri:content-entity ri:content-id="123"/>` | content id |

## 10. Emoticons

`<ac:emoticon ac:name="smile"/>` — names include `smile`, `sad`, `cheeky`, `laugh`, `wink`,
`thumbs-up`, `thumbs-down`, `information`, `tick`, `cross`, `warning`.

## 11. Templates (at:*) & placeholders

Template bodies (page templates / blueprints) add the `at:` namespace for variables, and
`<ac:placeholder>` for instructional text.
```xml
<at:declarations>
  <at:string at:name="MyText"/>
  <at:textarea at:columns="100" at:name="MyMulti" at:rows="5"/>
  <at:list at:name="MyList">
    <at:option at:value="Apples"/>
    <at:option at:value="Pears"/>
  </at:list>
</at:declarations>
<p>A variable: <at:var at:name="MyText"/></p>
<li><ac:placeholder>type here…</ac:placeholder></li>
<!-- mention-type placeholder: <ac:placeholder ac:type="mention">@mention…</ac:placeholder> -->
```
