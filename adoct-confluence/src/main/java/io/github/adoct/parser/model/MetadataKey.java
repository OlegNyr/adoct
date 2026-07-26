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
         * Версия страницы ({@code version.when}) — пишется в заголовок ({@code :confluency-version:} /
         * frontmatter {@code confluency-version}) и служит признаком изменения при инкрементальной выгрузке.
         */
        VERSION("version"),
        /** In-memory конвертация: не писать файлы — длинные блоки инлайнить, drawio отдавать ссылкой. */
        IN_MEMORY("inMemory");
    private final String  key;

}
