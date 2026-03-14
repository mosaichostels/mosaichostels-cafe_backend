package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        log.info("🔥 Starting Firebase initialization...");
        
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("ℹ️ Firebase already initialized, returning existing instance.");
            return FirebaseApp.getInstance();
        }

        InputStream stream = loadCredentials();
        if (stream == null) {
            log.error("❌ CRITICAL: No Firebase credentials found! Application will fail to start.");
            throw new RuntimeException("No Firebase credentials found!");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("✅ Firebase initialized successfully: {}", app.getName());
        return app;
    }

    private InputStream loadCredentials() {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            log.info("✅ Found FIREBASE_SERVICE_ACCOUNT_JSON environment variable.");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        log.info("🔍 Checking for firebase-service-account.json in classpath...");
        InputStream file = getClass().getClassLoader()
                .getResourceAsStream("firebase-service-account.json");
        if (file != null) {
            log.info("✅ Found firebase-service-account.json in classpath.");
            return file;
        }

        return null;
    }
}