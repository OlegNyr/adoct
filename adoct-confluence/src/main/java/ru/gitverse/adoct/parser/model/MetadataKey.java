package ru.gitverse.adoct.parser.model;

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
        /** In-memory конвертация: не писать файлы — длинные блоки инлайнить, drawio отдавать ссылкой. */
        IN_MEMORY("inMemory");
    private final String  key;

}
