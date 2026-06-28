
package ru.gitverse.adoct.parser.confluence.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.annotation.processing.Generated;

@Data
@Generated("jsonschema2pojo")
public class ResultDto {

    public String id;
    public String type;
    public String status;
    public String title;
    public Metadata metadata;
    public Extensions extensions;
    @JsonProperty("_links")
    public LinksDto links;
    public Expandable expandable;


}
