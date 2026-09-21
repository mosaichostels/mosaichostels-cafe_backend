package com.hostel.ordering.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebConfigTest {

    @Test
    void addCorsMappings_trimsConfiguredOrigins() {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "corsOrigins", "https://admin.example, https://staff.example ");
        CorsConfiguration cors = corsFor(webConfig);

        assertEquals(List.of("https://admin.example", "https://staff.example"), cors.getAllowedOrigins());
    }

    @Test
    void addCorsMappings_blankOriginFallsBackToLocalhostNotWildcard() {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "corsOrigins", " ");
        CorsConfiguration cors = corsFor(webConfig);

        assertEquals(List.of("http://localhost:3000"), cors.getAllowedOrigins());
        assertFalse(cors.getAllowedOrigins().contains("*"));
    }

    private CorsConfiguration corsFor(WebConfig webConfig) {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        webConfig.addCorsMappings(registry);
        return registry.configurations().get("/**");
    }

    private static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
