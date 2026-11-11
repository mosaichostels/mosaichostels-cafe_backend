package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the hub services for reading eZee reservation replies. */
final class PmsJson {

    private PmsJson() {}

    /** eZee sends a lone child as an object in some replies; treat it as a one-element list. */
    static List<JsonNode> elements(JsonNode n) {
        if (n.isArray()) {
            List<JsonNode> l = new ArrayList<>();
            n.forEach(l::add);
            return l;
        }
        return n.isObject() ? List.of(n) : List.of();
    }

    static String text(JsonNode n, String field) {
        return n.path(field).asText("").strip();
    }
}
