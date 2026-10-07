package com.hostel.ordering.controller;

import com.hostel.ordering.service.FCMNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final FCMNotificationService fcmNotificationService;

    public NotificationController(FCMNotificationService fcmNotificationService) {
        this.fcmNotificationService = fcmNotificationService;
    }

    @PostMapping("/subscribe")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public void subscribe(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token != null) {
            fcmNotificationService.subscribeToTopic(token);
        }
    }

    @PostMapping("/unsubscribe")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public void unsubscribe(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token != null) {
            fcmNotificationService.unsubscribeFromTopic(token);
        }
    }
}
