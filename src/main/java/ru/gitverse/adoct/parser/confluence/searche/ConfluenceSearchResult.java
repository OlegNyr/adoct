package ru.gitverse.adoct.parser.confluence.searche;

import lombok.Data;
import ru.gitverse.adoct.parser.confluence.content.ResultDto;

import java.util.List;

@Data
public class ConfluenceSearchResult {
    private List<ResultDto> results;
    private int start;
    private int limit;
    private int size;
    private String cqlQuery;
    private long searchDuration;
    private int totalSize;
}
