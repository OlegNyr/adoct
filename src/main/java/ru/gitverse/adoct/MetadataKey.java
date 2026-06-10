package ru.gitverse.adoct;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MetadataKey {
        LINKS("links"),
        TITLE("title"),
        URL("url"),
        CREATE("create"),
        ATTACH_FOLDER("attachFolder"),
        ATTACH_FOLDER_NAME("attachFolderName"),
        IMAGE("image"),
        DESTINATION_FOLDER("destinationFolder"),
        FILES_FOLDER("filesFolder"),
        FILES_FOLDER_NAME("filesFolderName"),
        COLOR("color");
    private final String  key;

}
