package com.hostel.ordering.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppVersionController {

    public record AndroidVersion(int latest, int min, String url) {}

    private final AndroidVersion version;

    // Unset properties give {0,0,""}: the app shows no update prompt.
    public AppVersionController(
            @Value("${app.android.latest-version-code:0}") int latest,
            @Value("${app.android.min-version-code:0}") int min,
            @Value("${app.android.apk-url:}") String url) {
        this.version = new AndroidVersion(Math.max(latest, 0), Math.max(min, 0), url == null ? "" : url.trim());
    }

    // Any authenticated user: SecurityConfig's anyRequest().authenticated() covers /app/**.
    @GetMapping("/app/android-version")
    public ResponseEntity<AndroidVersion> androidVersion() {
        return ResponseEntity.ok(version);
    }
}
