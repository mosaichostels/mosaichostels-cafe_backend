package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class PmsTestJson {

    private static final ObjectMapper OM = new ObjectMapper();

    private PmsTestJson() {}

    static JsonNode json(String s) {
        try {
            return OM.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
