package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    public void initialize() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            logger.info("Firebase already initialized");
            return;
        }

        InputStream stream = loadCredentials();
        if (stream == null) {
            throw new RuntimeException("❌ No Firebase credentials found!");
        }

        FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build());

        logger.info("✅ Firebase initialized successfully");
    }

    private InputStream loadCredentials() {
        // Option 1: Raw JSON string from environment variable (recommended for Railway)
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            logger.info("✅ Loading Firebase from FIREBASE_SERVICE_ACCOUNT_JSON env var");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        // Option 2: File fallback for local development
        InputStream file = getClass().getClassLoader()
                .getResourceAsStream("firebase-service-account.json");
        if (file != null) {
            logger.info("✅ Loading Firebase from firebase-service-account.json file");
            return file;
        }

        return null;
    }
}