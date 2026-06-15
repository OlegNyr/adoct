---
name: asciidoc-confluence
description: >-
  Authoring and converting AsciiDoc (.adoc) to render correctly in Confluence via the Confluence
  Publisher (asciidoc-confluence-publisher-maven-plugin / Gradle / Docker, by
  org.sahli.asciidoc.confluence.publisher). Use WHENEVER the target is Confluence and source is
  AsciiDoc: converting docs to Confluence, writing Confluence-ready .adoc, publishing
  technical/architecture docs to a space, setting up the Maven/Gradle/CI pipeline, or debugging why
  admonitions, code blocks, callouts, tables, cross-references, ToC, collapsible blocks, images or
  PlantUML render wrong. Trigger even on brief asks like "доку в Confluence", "опубликовать adoc в
  Confluence", "конвертировать asciidoc в confluence", "почему WARNING выглядит не так",
  "confluence-publisher", "asciidoctor confluence", or any .adoc bound for a Confluence space. The
  Publisher remaps admonitions and supports only a subset of AsciiDoc/Confluence, so consult this
  skill instead of relying on plain AsciiDoc knowledge.
---

# AsciiDoc → Confluence (Confluence Publisher)

## What this targets

This skill is specifically for the **Confluence Publisher** (`org.sahli.asciidoc.confluence.publisher`),
the de-facto tool for "docs-as-code → Confluence". It runs **AsciidoctorJ** to convert each `.adoc`
file into Confluence **XHTML storage format + macros**, uploads images/files as attachments, and
re-publishes only changed pages.

Two consequences drive everything below:

1. **It is not full AsciiDoc and not full Confluence.** Only a curated subset is supported. Using an
   unsupported construct silently produces a broken or empty result.
2. **Some constructs are remapped, not passed through.** Most importantly admonitions (see below).
   What renders is *not* what the AsciiDoc keyword suggests.

If the target is generic AsciiDoc → HTML/PDF (plain `asciidoctor`), this skill's Confluence-specific
remappings do **not** apply — say so and use standard AsciiDoc instead.

## Workflow

1. **Confirm the target is Confluence Publisher.** If the user is on plain asciidoctor/HTML/PDF, stop
   applying the Confluence remappings.
2. **Author/convert using only supported constructs** (cheat-sheet below; full detail in
   `references/formatting.md`). Prefer supported features; flag anything that has no clean mapping.
3. **Respect the source tree convention** — file/folder layout *is* the Confluence page hierarchy
   (see "Pages & structure").
4. **Proactively warn about the gotchas** — especially admonition remapping and guarded callouts —
   because they look fine in a local asciidoctor preview and only break once in Confluence.
5. **For setup/CI** (Maven, Gradle, Docker, credentials, ancestor/space config) → `references/publishing.md`.

## ⚠️ Gotcha #1 — Admonitions are REMAPPED

This is the single most common surprise. The AsciiDoc admonition does **not** become the
same-named Confluence panel:

| AsciiDoc        | Confluence macro (panel) | Typical color |
|-----------------|--------------------------|---------------|
| `[NOTE]`        | **info**                 | blue          |
| `[TIP]`         | **tip**                  | green         |
| `[CAUTION]`     | **note**                 | yellow        |
| `[WARNING]`     | **note**                 | yellow        |
| `[IMPORTANT]`   | **warning**              | red           |

So if the user wants a **red/alarming** panel in Confluence, write `[IMPORTANT]`, not `[WARNING]`.
If they want a yellow caution panel, `[WARNING]` or `[CAUTION]`. `[NOTE]` lands in the blue info
panel, not a generic note. Always confirm intent by the **target panel**, not the keyword name.

Admonition titles are supported: put `.Title` on the line directly above the block.

## ⚠️ Gotcha #2 — This is AsciiDoc, NOT Markdown

A frequent failure is writing Markdown syntax into a `.adoc` file. These are **wrong** and will not
render:

- Headings: use `==`, `===`, `====` — **not** `#`, `##`.
- Bold: `*bold*` — **not** `**bold**`. Italic: `_italic_` — **not** `*italic*`.
- Inline code: `` `code` `` (same), but code blocks use `[source,lang]` + `----` delimiters,
  **not** triple-backtick fences.
- Links: `link:https://x[label]` or `https://x[label]` — **not** `[label](url)`.
- Lists: `*` / `.` for bullets/numbers (same idea), but no `-` for bullets is the AsciiDoc norm
  (`*` is canonical).
- No `> ` blockquotes — use `[quote]` blocks if needed.

When converting *from* Markdown, translate every one of these, don't pass them through.

## Formatting cheat-sheet (supported)

**Inline** (all map to Confluence directly):

| Want            | AsciiDoc                          |
|-----------------|-----------------------------------|
| bold            | `*bold*`                          |
| italic          | `_italic_`                        |
| monospace       | `` `mono` ``                      |
| mono+bold+ital  | `` `*_text_*` ``                  |
| superscript     | `^super^`                         |
| subscript       | `~sub~`                           |
| external link   | `link:https://x[Label]`           |
| inline image    | `image:path.png[]`                |

**Blocks:**

- **Headings/sections:** `== L1`, `=== L2`, … up to `====== L5`. Alignment via role: `[.text-center]`
  on the line above (`text-left|center|right|justify`).
- **Code:** `[source,java]` then `----` … `----`. See code section below.
- **Tables:** `[cols="N", options="header"]` + `|===` … `|===`. See tables section.
- **Admonitions:** `[NOTE]` / `[TIP]` / `[CAUTION]` / `[WARNING]` / `[IMPORTANT]` + `====` … `====`
  (remapped — see Gotcha #1).
- **Collapsible:** `.Title` + `[%collapsible]` + `====` … `====` → Confluence **expand** macro.
  (A plain non-collapsible example block becomes a **tip** admonition.)
- **Block image:** `image::path.png[]` (caption via `.Caption` above; `width=`, `height=`,
  `border=true`, `link=`).
- **ToC:** `:toc:` right under the title (top of page), or `:toc: macro` + `toc::[]` for a custom
  location. `:toclevels: N` controls depth.

## Code blocks (high-value detail)

- Source blocks use the Confluence **code macro**. **Only these languages are accepted** (others
  fall back / break highlighting): `actionscript3, applescript, bash, c#, cpp, css, coldfusion,
  delphi, diff, erl, groovy, xml, java, jfx, js, php, perl, text, powershell, py, ruby, rust, sql,
  sass, scala, toml, vb, yml`. Note Confluence spellings: `py` (not python), `js`, `yml`, `c#`.
- **Line numbers:** add the `linenums` option: `[source,java,linenums]`. With **no** language use
  `[source%linenums]` (the comma form `[source,linenums]` does not work). Custom start: `start=4`.
- **Collapsed by default:** `[source,java,title="…",collapse=true]` (non-standard, Publisher-only).
- **Callouts — use GUARDED callouts.** Confluence's code macro can't host real callout bubbles, so
  put the `<1>` marker inside a comment so the code stays valid if copied:

  ```
  [source,java,linenums]
  ----
  public class MyClass { // <1>
      public static void main(String[] args){
          System.out.println("Hello world!"); // <2>
      }
  }
  ----
  <1> Definition of a Java class
  <2> Print to standard out
  ```

  For XML use the comment form: `<hello>world</hello><!--1-->`.
- AsciiDoc source highlighters (CodeRay, Rouge, etc.) are **disabled** — Confluence does the
  highlighting. Features like listing filenames are not supported.
- **Literal block** (`....`) → preformatted text. **Listing block** (`----` with no `[source]`) →
  Confluence **noformat** macro.

## Tables (high-value detail)

- Header: `options="header"` — **only column headers are supported, not row headers.**
- Column count: `[cols="3"]` or per-column specs.
- **Col span:** prefix the cell with `N+|` (e.g. `2+| spans two cols`).
- **Row span:** prefix with `.N+|` (e.g. `.2+| spans two rows`).
- **Rich cell** (lists, nested blocks): use the AsciiDoc cell operator `a|`.
- **Width:** `width="100%"` or `width="400px"` — but the **new Confluence editor ignores explicit
  widths** and snaps to default/wide/full-width layout. Don't promise pixel-perfect widths.

## Pages & structure

- **Each `.adoc` file = one Confluence page.** It **must** start with a document title `= Title`.
- **Titles must be unique across the whole target space** (Confluence constraint). The filename is
  *not* used as the title. Use the plugin's prefix/suffix config for uniqueness when needed.
- **Page hierarchy = folder layout.** A page's children live in a sibling folder named after the file:

  ```
  <root>
  +- top-level-page.adoc
  +- top-level-page/            # children + resources of top-level-page
     +- sub-page-one.adoc
     +- sub-page-two.adoc
     +- images/ ...
  ```
- **Includes must be prefixed with `_`** (e.g. `include::_shared.adoc[]`). Underscore-prefixed files
  are inlined into the referencing page and do **not** become their own page.
- **Labels:** `:keywords: a, b, c` in page metadata → Confluence labels.
- **Cross-references between pages:** `<<sub-page.adoc#, Label>>` (path relative to the referencing
  file, target must be under the doc root). Anchors: `<<file.adoc#anchor, Label>>`.
- **In-page links:** inline anchor `[[id]]` or section id `[#id]`, referenced with `<<id>>`.
- **Images/PlantUML/other files** referenced from a page are auto-uploaded as **attachments**,
  resolved relative to the referencing file. Remote image URLs are linked, not downloaded.
  PlantUML (`[plantuml, name, png]` or `plantuml::file.puml[]`, incl. C4-PlantUML) renders to an
  attached PNG.

## When to read the references

- `references/formatting.md` — the complete, example-by-example mapping for every supported
  construct. Read it when authoring/converting a non-trivial document or when a specific construct's
  exact syntax/limitation matters.
- `references/publishing.md` — Maven plugin, Gradle plugin, Docker image, source structure config,
  credentials, ancestor/space, REST API version, CI integration. Read it for any setup/publish/CI task.
