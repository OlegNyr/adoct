package io.github.adoct.parser.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MetadataKey {
        LINKS("links"),
        TITLE("title"),
        PAGE_ID("pageId"),
        URL("url"),
        CREATE("create"),
        /** Ключ пространства Confluence → {@code :confluency-space:} / frontmatter {@code confluency-space}. */
        SPACE("space"),
        /** Автор (создатель) страницы → {@code :confluency-author:} / frontmatter {@code confluency-author}. */
        AUTHOR("author"),
        /** Дата создания страницы → {@code :confluency-created:} / frontmatter {@code confluency-created}. */
        CREATED("created"),
        ATTACH_FOLDER("attachFolder"),
        ATTACH_FOLDER_NAME("attachFolderName"),
        IMAGE("image"),
        DESTINATION_FOLDER("destinationFolder"),
        FILES_FOLDER("filesFolder"),
        FILES_FOLDER_NAME("filesFolderName"),
        COLOR("color"),
        /** Метки страницы Confluence ({@code List<String>}) → {@code :keywords:} (adoc) / {@code tags} (md). */
        LABELS("labels"),
        /**
         * Прямые дочерние страницы ({@code List<LinkResult>}: заголовок + относительный путь на её
         * {@code index.<ext>}) — для макроса {@code children} (статический список ссылок).
         */
        CHILDREN("children"),
        /**
         * Версия страницы ({@code version.when}) — пишется в заголовок ({@code :confluency-version:} /
         * frontmatter {@code confluency-version}) и служит признаком изменения при инкрементальной выгрузке.
         */
        VERSION("version"),
        /** In-memory конвертация: не писать файлы — длинные блоки инлайнить, drawio отдавать ссылкой. */
        IN_MEMORY("inMemory");
    private final String  key;

}
