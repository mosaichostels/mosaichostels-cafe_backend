package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void initialize() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized");
            return;
        }

        InputStream stream = loadCredentials();
        if (stream == null) {
            throw new RuntimeException("❌ No Firebase credentials found!");
        }

        FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build());

        log.info("✅ Firebase initialized successfully");
    }

    private InputStream loadCredentials() {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            log.info("✅ Loading Firebase from FIREBASE_SERVICE_ACCOUNT_JSON env var");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        InputStream file = getClass().getClassLoader()
                .getResourceAsStream("firebase-service-account.json");
        if (file != null) {
            log.info("✅ Loading Firebase from firebase-service-account.json file");
            return file;
        }

        return null;
    }
}