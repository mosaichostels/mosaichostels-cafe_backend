package com.hostel.ordering.config;

import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Provides a GoogleCredentials bean scoped to FCM only.
 *
 * WHY we replaced firebase-admin with this:
 * firebase-admin 9.4.1 transitively pulls in grpc-netty-shaded, netty-codec-http2,
 * protobuf-java, opencensus, guava, and 40+ other JARs (~70MB on disk).
 * On Railway's constrained containers this inflates JVM metaspace past the container
 * memory limit DURING class-scanning at startup — the kernel OOM-kills the process
 * with SIGKILL before any Java exception can be logged, producing the silent restart loop.
 *
 * The FCM HTTP v1 REST API needs only an OAuth2 bearer token, which GoogleCredentials
 * provides as a single ~2MB dependency with no gRPC or Netty involved at all.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    /**
     * Returns null (and logs a warning) if credentials are absent.
     * FCMNotificationService checks for null and skips notifications gracefully.
     */
    @Bean
    public GoogleCredentials firebaseCredentials() {
        try {
            InputStream stream = loadCredentials();
            if (stream == null) {
                log.warn("⚠️  No Firebase credentials found. Push notifications will be disabled. " +
                         "Set FIREBASE_SERVICE_ACCOUNT_JSON env var to enable them.");
                return null;
            }
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(stream)
                    .createScoped(Collections.singleton(FCM_SCOPE));
            log.info("✅ Firebase credentials loaded successfully.");
            return credentials;
        } catch (IOException e) {
            log.error("❌ Failed to load Firebase credentials — push notifications disabled.", e);
            return null;
        }
    }

    private InputStream loadCredentials() {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            log.info("✅ Using FIREBASE_SERVICE_ACCOUNT_JSON env var for Firebase credentials.");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        InputStream file = getClass().getClassLoader()
                .getResourceAsStream("firebase-service-account.json");
        if (file != null) {
            log.info("✅ Using firebase-service-account.json from classpath.");
            return file;
        }
        return null;
    }
}
