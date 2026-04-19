package com.hostel.ordering.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "categories")
public class Category {
    @Id
    private String id;
    private String name;
    @JsonProperty("showOrder")
    private int showOrder;
    @JsonProperty("type")
    private String type;

    public static final String TYPE_MENU = "MENU";
    public static final String TYPE_ESSENTIAL = "ESSENTIAL";

    public Category() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getShowOrder() { return showOrder; }
    public void setShowOrder(int showOrder) { this.showOrder = showOrder; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}