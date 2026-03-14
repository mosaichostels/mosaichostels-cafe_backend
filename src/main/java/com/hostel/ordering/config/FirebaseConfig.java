package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() {
        log.info("🔥 Starting Firebase initialization...");
        
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("ℹ️ Firebase already initialized, returning existing instance.");
                return FirebaseApp.getInstance();
            }

            InputStream stream = loadCredentials();
            if (stream == null) {
                log.error("❌ CRITICAL: No Firebase credentials found! Expected FIREBASE_SERVICE_ACCOUNT_JSON env var or firebase-service-account.json file.");
                // We don't throw here to let the app start (maybe some features work without Firebase),
                // but usually the app will fail later.
                return null; 
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("✅ Firebase initialized successfully: {}", app.getName());
            return app;
        } catch (Exception e) {
            log.error("❌ CRITICAL: Firebase initialization failed!", e);
            return null;
        }
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