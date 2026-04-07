package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream stream = loadCredentials();
            if (stream == null) {
                throw new RuntimeException("❌ No Firebase credentials found!");
            }
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build());
            logger.info("✅ Firebase initialized successfully");
        } else {
            logger.info("Firebase already initialized");
        }
        return FirebaseMessaging.getInstance();
    }

    private InputStream loadCredentials() {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            logger.info("✅ Loading Firebase from FIREBASE_SERVICE_ACCOUNT_JSON env var");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        InputStream file = getClass().getClassLoader()
                .getResourceAsStream("firebase-service-account.json");
        if (file != null) {
            logger.info("✅ Loading Firebase from firebase-service-account.json file");
            return file;
        }

        return null;
    }
}
