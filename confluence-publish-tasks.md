# AsciiDoc → Confluence: бэклог доработок

Сравнение нашего импортёра (`ru.gitverse.adoct.generate.*` + `service/PublishDocsToConfluence`)
с **Confluence Publisher** (`org.sahli.asciidoc.confluence.publisher`, склонирован в
`D:/IdeaProjects/confluence-publisher-ref`, вне нашего git). Идём по задачам сверху вниз.

Ссылки на их код:
- конвертер: `confluence-publisher-ref/asciidoc-confluence-publisher-converter/src/main/.../AsciidocConfluencePage.java`
- шаблоны: `.../converter/templates/*.html.slim` + `helpers.rb`
- иерархия: `.../FolderBasedAsciidocPagesStructureProvider.java`
- клиент/дельта: `.../asciidoc-confluence-publisher-client/.../ConfluencePublisher.java`, `ConfluenceRestV1Client.java`
- CLI: `.../asciidoc-confluence-publisher-cli/.../*`

Наши целевые файлы: `generate/render/StorageRenderer.java`, `StorageFormat.java`, `InlineNormalizer.java`,
`generate/confluence/ConfluenceClient.java`, `service/PublishDocsToConfluence.java`, тесты в `generate/render`.

Легенда статусов: `[ ]` todo · `[~]` в работе · `[x]` готово.

---

## A. Корректность конвертации (баги — выдаём неверный storage). ПРИОРИТЕТ.

### [x] A1. Admonitions (`[NOTE]/[TIP]/[CAUTION]/[WARNING]/[IMPORTANT]`)
> Сделано: `StorageFormat.admonitionMacroName` + `StorageRenderer.renderAdmonition` (rich-text-body, title, блочная/однострочная формы). Тест `AdmonitionRenderTest`.
- **Проблема.** В `StorageRenderer.renderNode` нет ветки контекста `"admonition"` → уходит в `default` →
  рекурсия по детям → панель теряется, остаётся голый текст.
- **Как у них** (`block_admonition.html.slim`) — remap имён (НЕ совпадают с AsciiDoc!):
  `note→info`, `tip→tip`, `caution→note`, `warning→note`, `important→warning`; тело в `ac:rich-text-body`,
  опц. `ac:parameter name="title"`.
- **Что делать.** Ветка `case "admonition"`: имя берётся из `node.getAttribute("name")` (note/tip/...),
  remap по таблице → `<ac:structured-macro ac:name="info|tip|note|warning">` + title (если есть) +
  `<ac:rich-text-body>` с рекурсией `renderBlocks(node.getBlocks())` (или `inline(content(node))` для простого).
  Добавить билдер `StorageFormat.richTextMacro(name, titleOrNull, bodyXhtml)`.
- **Файлы.** `StorageRenderer`, `StorageFormat`.
- **Тест.** `AdmonitionRenderTest`: `[WARNING]` → `<ac:structured-macro ac:name="note">`; `[IMPORTANT]` → `warning`;
  с `.Title` → `ac:parameter name="title"`.
- **Риск.** Низкий.

### [x] A2. Инлайн-картинки (`image:x[]`) ломаются
> Сделано: `InlineNormalizer.inlineImage` (проход по `<img>`): локальный существующий → `ri:attachment`+вложение, внешний URL → `ri:url`, отсутствующий → как есть. `StorageFormat.inlineImageAttachment/inlineImageUrl`. Тест `InlineImageRenderTest`.
- **Проблема.** Конвертируем только блочные (`image::`, контекст `image`). Инлайн приходит внутри
  `getContent()` как `<img src=...>` и остаётся `<img/>` — в storage битая картинка, файл не во вложениях.
- **Как у них** (`inline_image.html.slim`) — `<ac:image ac:inline="true" ...><ri:attachment ri:filename=.../></ac:image>`.
- **Что делать.** В `InlineNormalizer.normalize` добавить проход по `<img src="X" .../>`: если `X` — не URL и
  файл существует относительно baseDir → собрать во вложения + заменить на `ac:image` (inline);
  иначе оставить/выкинуть. Учесть `alt`.
- **Файлы.** `InlineNormalizer`, `StorageFormat` (`inlineImage(fileName, alt)`).
- **Тест.** В `FileLinkRenderTest`/новый: абзац с `image:pic.png[]` при существующем файле → `ac:image` + attach.
- **Риск.** Средний (регэксп по `<img>`; не задеть уже собранные `ac:image`).

### [x] A3. Язык кода + `noformat`
> Сделано: `StorageFormat.confluenceLang` (маппинг+whitelist), `codeMacro` (language/title/linenums/firstline/collapse), `noformatMacro`; `StorageRenderer.renderListing` различает plantuml/source/прочее. Тесты в `MacroRenderTest`. NB: плоский `----` теперь `noformat` (было `code`).
- **Проблема.** `renderListing` всегда пишет макрос `code` без `language`; теряется подсветка. Литералы тоже идут как `code`.
- **Как у них** (`block_listing.html.slim` + `helpers.rb` `map_to_confluence_supported_lang`/`confluence_supported_lang`):
  маппинг (`python→py`, `yaml→yml`, `html→xml`, `json→js`, `sh→bash`, `cs/csharp→c#`, …), `language` только для
  поддерживаемого набора; плюс `linenumbers`(linenums), `firstline`(start), `collapse`, `title`.
  `style != source` → макрос **`noformat`**.
- **Что делать.** В `renderListing`: взять `node.getAttribute("language")`, прогнать через маппинг + whitelist;
  если style не `source`/literal → `noformat`. Параметры linenums/start/collapse/title — опционально (фаза 2).
  Маппинг и whitelist портировать из `helpers.rb`.
- **Файлы.** `StorageRenderer`, `StorageFormat` (расширить `macro` параметрами или новый `codeMacro(lang, params, body)`).
- **Тест.** `[source,python]` → `language` `py`; `[source,unknownlang]` → без language; чистый `----` → `noformat`.
- **Риск.** Низкий.

### [x] A4. Межстраничные ссылки без `ri:space-key`
> Сделано: `StorageFormat.pageLink` принимает spaceKey (атрибут добавляется только при непустом); прокинуто через `InlineNormalizer` и новый 6-арг конструктор `StorageRenderer`; `PublishDocsToConfluence` передаёт space (для папки — резолвленный, для файла — `getSpaceKey(pageId)`). Тест `crossDocLinkIncludesSpaceKeyWhenProvided`.
- **Проблема.** `StorageFormat.pageLink`/cross-doc не ставит `ri:space-key`. У них коммент: для нового
  редактора Confluence (баг CONFCLOUD-69902) без `ri:space-key` апдейт ломается.
- **Что делать.** Прокинуть `spaceKey` в `InlineNormalizer`/`StorageFormat.pageLink` →
  `<ri:page ri:content-title="..." ri:space-key="...">`. spaceKey уже резолвится в `PublishDocsToConfluence`
  (`client.getSpaceKey`) — передать в `StorageRenderer` ctor.
- **Файлы.** `StorageRenderer` ctor (+spaceKey), `InlineNormalizer`, `StorageFormat.pageLink`, `PublishDocsToConfluence` (передать spaceKey; для одиночного publish — `getSpaceKey(pageId)`).
- **Тест.** cross-doc ссылка → содержит `ri:space-key="DS"`.
- **Риск.** Низкий, но трогает сигнатуры — проверить существующие link-тесты (ожидания обновить).

### [x] A5. Callouts (`<1>` в коде и списке)
> Сделано минимально: контекст `colist` (пояснения к callout'ам) → нумерованный список (`renderList(ol)`). Маркеры `<1>` в коде остаются в сыром источнике как есть. Тест `calloutListBecomesOrderedList`.
- **Проблема.** Не обрабатываем; в коде/тексте останутся «сырые» маркеры.
- **Как у них** (`inline_callout.html.slim`): guarded → `<!--(n)-->`, иначе `guard(n)`.
- **Что делать.** Низкий приоритет; оценить после A1–A4. Возможно отложить.
- **Риск.** Низкий, ценность ниже A1–A4.

---

## B. Недостающие фичи конвертации (не баги — просто нет). СРЕДНИЙ ПРИОРИТЕТ.

### [x] B1. `:keywords:` → labels Confluence
> Сделано: `ConfluenceClient.addLabels` (POST `/label`, prefix global); `PublishDocsToConfluence.parseKeywords` + `applyLabels` после каждой публикации (file/root/folder/leaf). Unit-тест `parseKeywords`.
- **Как у них.** `keywords(document)` из атрибута `keywords`, split по запятой → потом клиент шлёт `/label`.
- **Что делать.** В движке вернуть keywords из `Document` (расширить `RenderResult` или отдельный геттер).
  В `PublishDocsToConfluence` после create/update — выставить labels. Нужен метод в `ConfluenceClient` (`addLabels`).
- **Файлы.** `RenderResult` (+labels) или новый канал, `ConfluenceClient`, `PublishDocsToConfluence`.

### [x] B2. TOC (`toc::[]`)
> Сделано: контекст `toc` → `StorageFormat.tocMacro(maxLevel)`, maxLevel из `:toclevels:` (деф. 2). Тест `TocRenderTest`.
- **Как у них** (`_toc.html.slim`): `<ac:structured-macro ac:name="toc"><ac:parameter name="maxLevel">N`.
- **Что делать.** Ветка контекста `"toc"` (и атрибут `toc-placement`) → макрос toc с `maxLevel` из `toclevels`.
- **Файлы.** `StorageRenderer`, `StorageFormat`.

### [x] B3. Секционные якоря + выравнивание (sectnums отложены)
> Сделано: anchor-макрос по `section.getId()` в каждом заголовке; роль `text-left/right/center/justify` → инлайн-`style` на `<hN>`/`<p>` (`StorageRenderer.alignmentStyle`). Тесты в `HeadingRenderTest`/`InlineFormattingRenderTest`. sectnums (нумерация в заголовке) — НЕ делал (редко, отдельно при необходимости).
- **Как у них** (`section.html.slim`, `block_paragraph.html.slim`): в каждый `<hN>` вшит `anchor`-макрос
  (секции линкуемы); role `text-left/center/right/justify` → инлайн-`style`; `sectnums` в заголовке.
- **Что делать.** В `renderSection`/paragraph: добавить anchor-макрос по `section.getId()`, поддержать role→style.
- **Файлы.** `StorageRenderer`, `StorageFormat`.

### [x] B4. Таблицы: colspan/rowspan/footer/caption/width (rich-cell отложен)
> Сделано: `renderRows` (thead/tbody/tfoot), `cellOpenTag` (colspan/rowspan>1), caption из title, width→`style`, header-ячейки в теле → `th`. Тесты в `TableRenderTest`. Rich `a|`-ячейки рендерятся как текст (`inline(getText())`) — отдельная задача при необходимости.
- **Как у них** (`block_table.html.slim`): head/foot/body, `colspan`/`rowspan` из ячейки, `a|` (asciidoc cell) →
  `div =cell.content`, verse/literal стили, caption, width.
- **Что делать.** Расширить `renderTable`: colspan/rowspan атрибуты ячеек, footer, rich-cell (вложенный рендер
  `cell.getContent()` для style `:asciidoc`), caption/width. (На стороне нашего парсера Confluence→AsciiDoc
  colspan/rowspan уже есть — переиспользовать подход.)
- **Файлы.** `StorageRenderer`.

### [x] B5. Атрибуты картинок (width/height/alt/title/link)
> Сделано: `StorageFormat.image(file, alt, title, width, height)` (+ac:custom-width), обёртка `<a href>` при `link=` в `StorageRenderer.renderImage`. Тесты в `ImageRenderTest`. border — не делал (ниша).
- **Как у них** (`block_image.html.slim`): `ac:height/width/title/alt/border/custom-width`, опц. обёртка в `<a href>`.
- **Что делать.** В `renderImage` читать атрибуты узла и прокидывать в `ac:image`.
- **Файлы.** `StorageRenderer`, `StorageFormat.image(...)` (расширить).

### [x] B6. example/quote/sidebar блоки
> Сделано: `example` → `tip` (или `expand` при `[%collapsible]`), `quote` → `<blockquote>` + атрибуция, `sidebar` → макрос `panel`. Общий `StorageRenderer.renderRichMacro` (его же переиспользует admonition). Тесты `BlockRenderTest`.
- **Как у них** (`block_example.html.slim`): collapsible example → `expand`-макрос; обычный example → `tip`.
- **Что делать.** Ветки контекстов; оценить нужность.

---

## C. Надёжность публикации (клиент). СРЕДНИЙ/ВЫСОКИЙ ПРИОРИТЕТ для прод-использования.

### [x] C1. Дельта-выгрузка по content-hash (самое ценное)
> Сделано: `ConfluenceClient.getProperty/setProperty/deleteProperty` (content-property, set = delete+create). `PublishDocsToConfluence`: `updateBody` сравнивает sha256 тела с `content-hash` и пропускает неизменённое (без version-bump); новые страницы получают хэш (`rememberContentHash`); `uploadAttachments` пропускает вложения с совпавшим хэшом (ключ `sha256(filename)+"-attachment-hash"`). Итог считает `skipped`. Юнит-тест `sha256MatchesKnownVector`; REST — вручную.
- **Как у них** (`ConfluencePublisher.java`): sha256(контент) хранится как **page property** `content-hash`;
  перед update сравнивают существующий property с новым hash — если совпало и title тот же, **страницу
  не трогают** (нет version-bump, нет запроса). Аналогично per-attachment: ключ `hash(filename)+"-attachment-hash"`.
- **Проблема у нас.** Всегда `version+1` → мусорные версии, лишний трафик, шум в watchers.
- **Что делать.** В `ConfluenceClient` добавить `getProperty/setProperty/deleteProperty(pageId,key)` (REST
  `/rest/api/content/{id}/property`). В `PublishDocsToConfluence.updateBody`/leaf — считать sha256, сравнивать,
  пропускать неизменённое; после апдейта писать property. То же для вложений (`uploadAttachments`).
- **Файлы.** `ConfluenceClient`, `PublishDocsToConfluence`.
- **Тест.** Хэш-хелпер (чистый) юнит-тестом; REST — вручную.

### [x] C2. Прочая устойчивость REST
> Сделано: `HttpClient` connect-timeout 30s, request-timeout 60s; `ensureSuccess` извлекает `message` из JSON-ошибки Confluence (`errorReason`). Тест `ConfluenceClientTest`. Rate-limit НЕ делал (для одиночной публикации/папки не нужен; потребовал бы Guava). Пагинация — не нужна (детей не листаем).
- **Как у них** (`ConfluenceRestV1Client.java`): пагинация дочерних (limit=25), Guava RateLimiter
  (`maxRequestsPerSecond`), proxy с auth, таймауты, богатые ошибки (тело ответа + message из JSON),
  Bearer/Basic (у нас только Bearer — ок для PAT).
- **Что делать.** По необходимости: богатые ошибки (включать тело ответа — у нас уже частично есть),
  таймауты на `HttpClient`, опц. rate-limit. Пагинация нужна только если будем листать детей (для orphan).
- **Файлы.** `ConfluenceClient`.

---

## E. Философские отличия (решения, не баги)

- **PlantUML.** Они рендерят локально в PNG (`asciidoctor-diagram`) → вложение. Мы вставляем серверный макрос
  (нужен плагин в Confluence). Оставляем наш подход (решение зафиксировать).
- **Иерархия.** Они: «файл + одноимённая папка» (`guide.adoc`+`guide/`). Мы: `index.adoc` = страница папки.
  Наш выбор осознанный — оставляем.
- **Includes.** Они: `_`-префикс файла = инлайн-инклюд, не страница. Мы: `include::*.adoc[]` → макрос
  «Include Page». Разные модели — оставляем нашу.

---

## Предлагаемый порядок

1. **A1–A4** (admonitions, инлайн-картинки, язык кода/noformat, ri:space-key) — корректность, низкий риск, без JRuby.
2. **C1** (дельта по content-hash) — крупный выигрыш для прод-публикации.
3. **B1–B5** (labels, TOC, секц.якоря/выравнивание, таблицы, атрибуты картинок) — по мере надобности.
4. **D1** (спайк по Slim-шаблонам) — стратегически; решает разом многое, но требует проверки JRuby/slim.
5. **A5, B6, C2, C3** — по остаточному принципу.
