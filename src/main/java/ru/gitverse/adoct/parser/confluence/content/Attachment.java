
package ru.gitverse.adoct.parser.confluence.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.annotation.processing.Generated;
import java.util.List;

@Data
@Generated("jsonschema2pojo")
public class Attachment {

    public List<ResultDto> results;
    public Integer start;
    public Integer limit;
    public Integer size;
    @JsonProperty("_links")
    public LinksDto links;



}
