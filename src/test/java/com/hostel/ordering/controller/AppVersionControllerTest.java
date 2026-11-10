package com.hostel.ordering.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppVersionControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode body(AppVersionController c) {
        return mapper.valueToTree(c.androidVersion().getBody());
    }

    @Test
    void configuredValuesUseExactFieldNames() {
        JsonNode j = body(new AppVersionController(12, 10, "https://x/app.apk"));
        assertEquals(3, j.size());
        assertEquals(12, j.get("latest").asInt());
        assertEquals(10, j.get("min").asInt());
        assertEquals("https://x/app.apk", j.get("url").asText());
    }

    @Test
    void defaultsMeanNoUpdatePrompt() {
        JsonNode j = body(new AppVersionController(0, 0, ""));
        assertEquals(0, j.get("latest").asInt());
        assertEquals(0, j.get("min").asInt());
        assertEquals("", j.get("url").asText());
    }

    @Test
    void negativeVersionsClampToZeroAndBlankUrlIsEmpty() {
        JsonNode j = body(new AppVersionController(-3, -1, "   "));
        assertEquals(0, j.get("latest").asInt());
        assertEquals(0, j.get("min").asInt());
        assertEquals("", j.get("url").asText());
    }

    // Auth is the filter chain's anyRequest().authenticated() (no permitAll for /app/**, so no token = 401);
    // the endpoint adds no role restriction, so STAFF, ADMIN and SUPERADMIN are all served.
    @Test
    void endpointAddsNoRoleRestriction() throws Exception {
        assertNull(AppVersionController.class.getAnnotation(PreAuthorize.class));
        assertNull(AppVersionController.class.getMethod("androidVersion").getAnnotation(PreAuthorize.class));
    }
}
