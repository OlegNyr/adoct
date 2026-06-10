# Formatting reference: AsciiDoc → Confluence (Confluence Publisher)

Complete, construct-by-construct mapping. Source of truth: the Confluence Publisher documentation
(AsciidoctorJ backend). Use this when the exact syntax or a specific limitation matters.

## Table of contents
- [Inline text](#inline-text)
- [Sections / headings](#sections--headings)
- [Paragraphs & alignment](#paragraphs--alignment)
- [Admonitions](#admonitions)
- [Code & listings](#code--listings)
- [Callouts](#callouts)
- [Tables](#tables)
- [Images](#images)
- [Collapsible blocks](#collapsible-blocks)
- [Table of contents macro](#table-of-contents-macro)
- [Links, anchors, cross-references](#links-anchors-cross-references)
- [Includes](#includes)
- [Attachments](#attachments)
- [PlantUML](#plantuml)
- [Page metadata](#page-metadata)
- [Unsupported / partially supported](#unsupported--partially-supported)

---

## Inline text

| AsciiDoc                | Renders in Confluence as            |
|-------------------------|-------------------------------------|
| `*bold*`                | bold                                |
| `_italic_`              | italic                              |
| `` `monospaced` ``      | monospaced                          |
| `` `*_mono bold ital_*` `` | monospaced + bold + italic       |
| `^super^script`         | superscript                         |
| `~sub~script`           | subscript                           |

All map straight through. Combine markers by nesting them (mono outermost, then bold, then italic).

---

## Sections / headings

`= Title` is the page title (level 0, exactly one per file). Sub-sections:

```
== Section Level 1
=== Section Level 2
==== Section Level 3
===== Section Level 4
====== Section Level 5
```

**Alignment** of a heading via role on the preceding line:

```
[.text-right]
=== Right aligned heading
```

`text-<alignment>` where alignment ∈ `left | center | right | justify`.

---

## Paragraphs & alignment

Plain paragraphs become Confluence paragraphs and support all inline styles above.

**Paragraph title** (`.text`) renders with CSS class `cp-paragraph-title`:

```
.This is the title of a paragraph
The paragraph body follows on the next line.
```

**Alignment** — same role mechanism as headings:

```
[.text-center]
This text is centered.
```

---

## Admonitions

**Remapped — the keyword is NOT the panel.** Memorize this table:

| AsciiDoc      | Confluence macro |
|---------------|------------------|
| `[NOTE]`      | `info`           |
| `[TIP]`       | `tip`            |
| `[CAUTION]`   | `note`           |
| `[WARNING]`   | `note`           |
| `[IMPORTANT]` | `warning`        |

Syntax (with optional title):

```
[NOTE]
.Note Title
====
this is a note (renders in the blue INFO panel)
====
```

To get the **red Confluence "warning" panel**, use `[IMPORTANT]`. To get the **yellow "note"
panel**, use `[WARNING]` or `[CAUTION]`. `[NOTE]` → blue info panel. `[TIP]` → green tip panel.

---

## Code & listings

Three block kinds map differently:

| AsciiDoc block            | Confluence result            |
|---------------------------|------------------------------|
| Literal block `....`      | pre-formatted text           |
| Listing block `----` (no `[source]`) | `noformat` macro  |
| Source block `[source,lang]` `----` | `code` macro          |

A block **title** (`.Title` line above) becomes the macro's panel title.

### Supported source languages (Confluence Code Block Macro)

`actionscript3, applescript, bash, c#, cpp, css, coldfusion, delphi, diff, erl, groovy, xml, java,
jfx, js, php, perl, text, powershell, py, ruby, rust, sql, sass, scala, toml, vb, yml`

(`yml` requires Confluence ≥ 6.7.) Use Confluence's spellings: `py` not `python`, `js` not
`javascript`, `c#`, `yml`. An unsupported language breaks highlighting.

### Line numbers

```
[source,java,linenums]
----
public class MyCode { }
----
```

- Global: set `:source-linenums-option:` to enable for all source blocks on the page.
- No language? Use `[source%linenums]` (NOT `[source,linenums]`).
- Custom start: `[source,java,linenums,start=4]`.

### Source from external files

```
[source,java]
----
include::../files/Source.java[]
----
```

Partial by tag: `include::../files/Source.java[tags=myMethod]`.

### Collapsed source block (Publisher-only)

```
[source,java,title="Collapsed Source Block",collapse=true]
----
// hidden until expanded
----
```

### Highlighting

AsciiDoc highlighters (CodeRay/Rouge/Pygments) are auto-disabled; Confluence highlights instead.
Advanced features such as listing file names are not supported.

---

## Callouts

Use **guarded callouts** — put the `<n>` marker inside a comment so the snippet stays copy-pasteable
and valid (Confluence's code macro cannot render real callout bubbles):

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
<2> Print "Hello world!" to standard out
```

XML uses an XML comment as the guard:

```
[source,xml]
----
<hello>world</hello><!--1-->
----
<1> XML-style callout.
```

A non-guarded trailing `<3>` (outside a comment) still works but makes the code invalid if copied.

---

## Tables

```
[cols="2", options="header"]
|===
| Column 1
| Column 2

| A1
| A2

| B1
| B2
|===
```

- `options="header"` → first row is the header. **Only column headers; row headers unsupported.**
- Omit `options="header"` for a headerless table.
- Cells support inline styling (`*B1*`, `_A1_`).
- **Column span:** `2+| A1 & A2` (cell spans 2 columns).
- **Row span:** `.2+| A1` (cell spans 2 rows).
- **Rich cell** (lists / block content): use `a|`:

  ```
  |A1
  a|
  * A2
  * A3
  ```
- **Width:** `width="100%"` or `width="400px"`. The **new Confluence editor ignores explicit width**
  and maps to default / wide / full-width layout by its own logic — don't rely on exact widths.

---

## Images

Block image (auto-attached to the page, path relative to the `.adoc` file):

```
image::../images/frisbee.png[]
```

- **Caption:** `.A nice orange frisbee` on the line above (CSS class `cp-image-title`).
- **Size:** `image::x.png[width=100, height=50]` or `[height=75]`. Size respected for **block**
  images only; inline images size to surrounding font.
- **Inline:** `... text image:../images/frisbee.png[] more text` (single colon).
- **Remote:** `image::https://host/pic.jpg[]` — linked directly, **not** downloaded/attached.
- **Link target:** `image:x.png[width=16, height=16, link=https://…]`.
- **Border:** `image::x.png[border=true]` (works for inline too).

---

## Collapsible blocks

Collapsible example block → Confluence **expand** macro:

```
.Toggle Me
[%collapsible]
====
This content is revealed when the "Toggle Me" label is clicked.
====
```

- Title (`.Toggle Me`) is optional; default label is "Click here to expand...".
- A **non-collapsible** example block (`[example]` / bare `====`) becomes a **tip** admonition
  (Confluence has no example element).
- The `open` option on collapsible blocks is **not** supported.

---

## Table of contents macro

Top of page:

```
= Page Title
:toc:

== Some Section
```

`:toc:` must follow the title with no blank line between.

Custom location:

```
= Page Title
:toc: macro

toc::[]

== Some Section
```

`toc::[]` must be surrounded by blank lines. Depth: `:toclevels: 4` (default shows levels 1–2).
`toc-title` is **not** supported — emulate a title with bold text or a collapsible block title above
`toc::[]`.

---

## Links, anchors, cross-references

- **External, no label:** `link:http://github.com[]`
- **External, label:** `link:http://github.com[GitHub]`
- **Implicit:** a bare `http://github.com` URL auto-links.
- **Between pages:** `<<sub-page.adoc#, Label>>` — path relative to the referencing file; target must
  live under the documentation root.
- **To an anchor in another page:** `<<file.adoc#anchor-id, Label>>` (inline anchor or headline
  anchor like `#_inline_images`).
- **In-page inline anchor:** `[[paragraph-a]]Paragraph text` then `<<paragraph-a>>`.
- **Section anchor / custom id:** `[[section-a]]` or `[#section-b]` above the heading, then
  `<<section-a>>` / `<<section-b>>`.

---

## Includes

```
include::_included-content.adoc[]
```

- **Included files must be prefixed with `_`.** Underscore-prefixed `.adoc` files are inlined into the
  referencing page and do **not** create their own Confluence page.
- Resolved relative to the referencing file.

---

## Attachments

Referencing any **non-AsciiDoc** file auto-uploads it as a page attachment:

```
link:../files/attachment.txt[Attachment]   // explicit label
link:../files/attachment.txt[]              // filename used as label
```

URL-encode special characters in filenames: `link:../files/attachment%20with%20space.txt[Label]`.

---

## PlantUML

Rendered to a PNG via Asciidoctor Diagram, attached, and inserted as an image.

Embedded:

```
[plantuml, diagram-name, png]
....
class Alpha
class Beta
Alpha <|-- Beta
....
```

Included file: `plantuml::../files/diagram.puml[]`.

C4-PlantUML is supported, via remote include
(`!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml`)
or bundled stdlib (`!include <C4/C4_Container>`, no internet needed).

---

## Page metadata

- **Title:** `= Title` (required, unique across the space).
- **Labels:** `:keywords: label-one, label-two`.
- **Built-in source attributes** (exposed by the Publisher, usable as `{…}` in content):
  - `{cp-source-path}` — relative path, e.g. `pages/user-guide.adoc`
  - `{cp-source-file}` — filename incl. extension, e.g. `user-guide.adoc`
  - `{cp-source-name}` — filename without extension, e.g. `user-guide`

---

## Unsupported / partially supported

The Publisher deliberately covers a subset. Known limits to design around:

- Row headers in tables — not supported (column headers only).
- Explicit table widths — ignored by the new Confluence editor.
- `toc-title` attribute — not supported.
- `open` option on collapsible blocks — not supported.
- AsciiDoc source highlighters and listing filenames — not supported.
- Inline image explicit sizing — sized to surrounding font, not honored.
- Non-collapsible example blocks — coerced into a tip admonition.

When a requested feature isn't supported, say so and offer the closest supported construct rather
than emitting AsciiDoc that will render empty or wrong.
