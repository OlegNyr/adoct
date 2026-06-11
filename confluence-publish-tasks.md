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

### [ ] A5. Callouts (`<1>` в коде и списке)
- **Проблема.** Не обрабатываем; в коде/тексте останутся «сырые» маркеры.
- **Как у них** (`inline_callout.html.slim`): guarded → `<!--(n)-->`, иначе `guard(n)`.
- **Что делать.** Низкий приоритет; оценить после A1–A4. Возможно отложить.
- **Риск.** Низкий, ценность ниже A1–A4.

---

## B. Недостающие фичи конвертации (не баги — просто нет). СРЕДНИЙ ПРИОРИТЕТ.

### [ ] B1. `:keywords:` → labels Confluence
- **Как у них.** `keywords(document)` из атрибута `keywords`, split по запятой → потом клиент шлёт `/label`.
- **Что делать.** В движке вернуть keywords из `Document` (расширить `RenderResult` или отдельный геттер).
  В `PublishDocsToConfluence` после create/update — выставить labels. Нужен метод в `ConfluenceClient` (`addLabels`).
- **Файлы.** `RenderResult` (+labels) или новый канал, `ConfluenceClient`, `PublishDocsToConfluence`.

### [ ] B2. TOC (`:toc:` / `toc::[]`)
- **Как у них** (`_toc.html.slim`): `<ac:structured-macro ac:name="toc"><ac:parameter name="maxLevel">N`.
- **Что делать.** Ветка контекста `"toc"` (и атрибут `toc-placement`) → макрос toc с `maxLevel` из `toclevels`.
- **Файлы.** `StorageRenderer`, `StorageFormat`.

### [ ] B3. Секционные якоря + выравнивание + sectnums
- **Как у них** (`section.html.slim`, `block_paragraph.html.slim`): в каждый `<hN>` вшит `anchor`-макрос
  (секции линкуемы); role `text-left/center/right/justify` → инлайн-`style`; `sectnums` в заголовке.
- **Что делать.** В `renderSection`/paragraph: добавить anchor-макрос по `section.getId()`, поддержать role→style.
- **Файлы.** `StorageRenderer`, `StorageFormat`.

### [ ] B4. Богатые таблицы (colspan/rowspan/rich-cell/caption/width/footer)
- **Как у них** (`block_table.html.slim`): head/foot/body, `colspan`/`rowspan` из ячейки, `a|` (asciidoc cell) →
  `div =cell.content`, verse/literal стили, caption, width.
- **Что делать.** Расширить `renderTable`: colspan/rowspan атрибуты ячеек, footer, rich-cell (вложенный рендер
  `cell.getContent()` для style `:asciidoc`), caption/width. (На стороне нашего парсера Confluence→AsciiDoc
  colspan/rowspan уже есть — переиспользовать подход.)
- **Файлы.** `StorageRenderer`.

### [ ] B5. Атрибуты картинок (width/height/border/alt/link)
- **Как у них** (`block_image.html.slim`): `ac:height/width/title/alt/border/custom-width`, опц. обёртка в `<a href>`.
- **Что делать.** В `renderImage` читать атрибуты узла и прокидывать в `ac:image`.
- **Файлы.** `StorageRenderer`, `StorageFormat.image(...)` (расширить).

### [ ] B6. example/quote/sidebar блоки
- **Как у них** (`block_example.html.slim`): collapsible example → `expand`-макрос; обычный example → `tip`.
- **Что делать.** Ветки контекстов; оценить нужность.

---

## C. Надёжность публикации (клиент). СРЕДНИЙ/ВЫСОКИЙ ПРИОРИТЕТ для прод-использования.

### [ ] C1. Дельта-выгрузка по content-hash (самое ценное)
- **Как у них** (`ConfluencePublisher.java`): sha256(контент) хранится как **page property** `content-hash`;
  перед update сравнивают существующий property с новым hash — если совпало и title тот же, **страницу
  не трогают** (нет version-bump, нет запроса). Аналогично per-attachment: ключ `hash(filename)+"-attachment-hash"`.
- **Проблема у нас.** Всегда `version+1` → мусорные версии, лишний трафик, шум в watchers.
- **Что делать.** В `ConfluenceClient` добавить `getProperty/setProperty/deleteProperty(pageId,key)` (REST
  `/rest/api/content/{id}/property`). В `PublishDocsToConfluence.updateBody`/leaf — считать sha256, сравнивать,
  пропускать неизменённое; после апдейта писать property. То же для вложений (`uploadAttachments`).
- **Файлы.** `ConfluenceClient`, `PublishDocsToConfluence`.
- **Тест.** Хэш-хелпер (чистый) юнит-тестом; REST — вручную.

### [ ] C2. Прочая устойчивость REST
- **Как у них** (`ConfluenceRestV1Client.java`): пагинация дочерних (limit=25), Guava RateLimiter
  (`maxRequestsPerSecond`), proxy с auth, таймауты, богатые ошибки (тело ответа + message из JSON),
  Bearer/Basic (у нас только Bearer — ок для PAT).
- **Что делать.** По необходимости: богатые ошибки (включать тело ответа — у нас уже частично есть),
  таймауты на `HttpClient`, опц. rate-limit. Пагинация нужна только если будем листать детей (для orphan).
- **Файлы.** `ConfluenceClient`.

### [ ] C3. Orphan removal (опционально)
- **Как у них** (`OrphanRemovalStrategy`): рекурсивно удаляют со стороны Confluence страницы-дети, которых нет
  в источнике. Требует листинга детей (пагинация) + delete.
- **Решение.** Обсудить: нужна ли нам синхронизация-удаление. По умолчанию — НЕ удалять (безопаснее).

---

## D. Стратегия: перевод конвертера на Slim-шаблоны. ОТДЕЛЬНО, КРУПНО.

### [ ] D1. Шаблонный бэкенд вместо Java-обхода
- **Суть.** `Options.builder().backend("xhtml5").templateDirs(dir)` + набор `.slim` на тип узла (как у них).
  AsciiDoctor сам рендерит storage-XHTML; Java остаётся только пост-обработка (сбор вложений, резолв
  cross-ref по `.adoc`→title, unescape CDATA) — см. `AsciidocConfluencePage.convertedContent`.
- **Выгода.** Уходит ручной `StorageRenderer` + jsoup-починка; «бесплатно» получаем инлайн/admonitions/
  callouts/таблицы/выравнивание/TOC. Сильно меньше нашего кода и багов A1–A5/B*.
- **⚠️ Риск/блокер.** `.slim` исполняется JRuby и требует gem'ы `slim`+`tilt` на load-path (AsciidoctorJ их не
  тянет). Нужно решить, как поставлять (gem в ресурсах/`GEM_PATH`, либо ERB-шаблоны вместо Slim).
- **Решение.** Сначала закрыть A1–A4 в текущем Java-рендерере (быстро, без JRuby). D1 — отдельным
  исследовательским спайком: проверить подключение slim в нашей сборке (`runIde`/тест), оценить трудозатраты.
  Если slim не заводится — рассмотреть ERB-шаблоны или остаться на Java-обходе.

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
