package com.hostel.ordering.controller;

import com.hostel.ordering.model.AppVersion;
import com.hostel.ordering.service.AppVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app")
public class AppVersionController {

    private final AppVersionService appVersionService;

    public AppVersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
    }

    @GetMapping("/version")
    public ResponseEntity<AppVersion> getVersion() {
        AppVersion latest = appVersionService.getLatest();
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }

    @PostMapping("/publish")
    public ResponseEntity<AppVersion> publish(@RequestBody AppVersion request) {
        return ResponseEntity.ok(appVersionService.publish(request));
    }
}
