package ru.gitverse.adoct.parser.doc;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import ru.gitverse.adoct.MetadataKey;

import java.util.Map;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class ParseContext {

    public static final ParseContext EMPTY = ParseContext.builder().build();

    @Singular("option")
    Set<DispatherOption> options;

    @Builder.Default
    int listLevel = 0;

    @Builder.Default
    int innerTable = 0;

    /**
     * Максимальное количество строк которое оставляем в текущем файле
     */
    @Builder.Default
    int maxIncludeString = 10;

    @Builder.Default
    Map<MetadataKey, Object> metadata = Map.of();

    boolean workColor;

    @NonFinal
    private volatile int topHeader;

    public boolean isOption(DispatherOption dispatherOption) {
        return getOptions().contains(dispatherOption);
    }

    public ParseContext addOption(DispatherOption dispatherOption) {
        return this.toBuilder().option(dispatherOption).build();
    }

    public ParseContext addAddLevel() {
        return this.toBuilder().listLevel(this.getListLevel() + 1).build();
    }

    public ParseContext addInnerTable() {
        return this.toBuilder().innerTable(innerTable + 1).build();
    }

    public void setTopHeader(int topHeader) {
        this.topHeader = topHeader;
    }

}
