package com.hostel.ordering.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdCorsConfigTest {

    @Test
    void productionCorsRequiresExplicitOriginAndNeverDefaultsToWildcard() throws Exception {
        String prodConfig = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertTrue(prodConfig.contains("allowed-origins: ${CORS_ALLOWED_ORIGINS}"));
        assertFalse(prodConfig.contains("allowed-origins: ${CORS_ALLOWED_ORIGINS:*}"));
    }
}
