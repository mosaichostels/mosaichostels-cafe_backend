package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    /**
     * @Lazy — Firebase initialization is deferred until FCMNotificationService first uses it.
     * This prevents Firebase's heavy SDK initialization from happening at startup,
     * which was contributing to the OOM crash loop on Railway.
     *
     * The bean returns null gracefully if credentials are absent — the FCMNotificationService
     * already handles null and logs a warning instead of throwing.
     */
    @Bean
    @Lazy
    public FirebaseApp firebaseApp() {
        log.info("🔥 Initializing Firebase (lazy)...");

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("ℹ️ Firebase already initialized, returning existing instance.");
                return FirebaseApp.getInstance();
            }

            InputStream stream = loadCredentials();
            if (stream == null) {
                log.warn("⚠️ No Firebase credentials found (FIREBASE_SERVICE_ACCOUNT_JSON not set). " +
                         "Push notifications will be disabled. App will continue normally.");
                return null;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("✅ Firebase initialized successfully: {}", app.getName());
            return app;

        } catch (Exception e) {
            log.error("❌ Firebase initialization failed — push notifications disabled.", e);
            return null;
        }
    }

    private InputStream loadCredentials() {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            log.info("✅ Using FIREBASE_SERVICE_ACCOUNT_JSON environment variable.");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        log.info("🔍 Checking classpath for firebase-service-account.json...");
        InputStream file = getClass().getClassLoader()
                .getResourceAsStream("firebase-service-account.json");
        if (file != null) {
            log.info("✅ Found firebase-service-account.json in classpath.");
            return file;
        }

        return null;
    }
}
