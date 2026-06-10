
package ru.gitverse.adoct.client.content;

import javax.annotation.processing.Generated;
import java.util.LinkedHashMap;
import java.util.Map;

@Generated("jsonschema2pojo")
public class Result {

    public String id;
    public String type;
    public String status;
    public String title;
    public Metadata metadata;
    public Extensions extensions;
    public LinksAttachment links;
    public ExpandableAttachment expandable;
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
