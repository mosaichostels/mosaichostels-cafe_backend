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
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    public void initialize() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) return;

        String base64 = System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");

        InputStream stream;
        if (base64 != null && !base64.isBlank()) {
            // Production: load from environment variable
            stream = new ByteArrayInputStream(Base64.getDecoder().decode(base64.trim()));
            logger.info("Loading Firebase credentials from environment variable");
        } else {
            // Local dev fallback: load from file
            stream = getClass().getClassLoader()
                    .getResourceAsStream("firebase-service-account.json");
            logger.info("Loading Firebase credentials from file");
        }

        if (stream == null) throw new RuntimeException("❌ Firebase credentials not found!");

        FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build());

        logger.info("✅ Firebase initialized successfully");
    }
}