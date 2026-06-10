package ru.gitverse.adoct.client.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.annotation.processing.Generated;

@Data
@Generated("jsonschema2pojo")
public class ContentMainPage {

    public String id;
    public String type;
    public String status;
    public String title;
    public Space space;
    public Descendants descendants;
    public Version version;
    public Body body;
    @JsonProperty("_links")
    public LinksDto links;
}
