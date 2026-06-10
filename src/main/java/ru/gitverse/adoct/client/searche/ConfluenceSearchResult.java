package ru.gitverse.adoct.client.searche;

import lombok.Data;
import ru.gitverse.adoct.client.content.ResultDto;

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
