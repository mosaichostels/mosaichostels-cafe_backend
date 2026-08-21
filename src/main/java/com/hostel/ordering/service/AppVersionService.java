package com.hostel.ordering.service;

import com.hostel.ordering.model.AppVersion;
import com.hostel.ordering.repository.AppVersionRepository;
import org.springframework.stereotype.Service;

@Service
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;
    private final FCMNotificationService fcmNotificationService;

    public AppVersionService(AppVersionRepository appVersionRepository,
                              FCMNotificationService fcmNotificationService) {
        this.appVersionRepository = appVersionRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    public AppVersion getLatest() {
        return appVersionRepository.findById(AppVersion.LATEST_ID).orElse(null);
    }

    public AppVersion publish(AppVersion request) {
        AppVersion existing = getLatest();
        if (existing != null && request.getVersionCode() <= existing.getVersionCode()) {
            throw new IllegalArgumentException(
                    "versionCode must be greater than current latest (" + existing.getVersionCode() + ")");
        }

        AppVersion toSave = new AppVersion(
                AppVersion.LATEST_ID,
                request.getVersionCode(),
                request.getVersionName(),
                request.getDownloadUrl(),
                request.getReleaseNotes(),
                System.currentTimeMillis());

        AppVersion saved = appVersionRepository.save(toSave);
        fcmNotificationService.sendAppUpdateNotification(
                saved.getVersionName(), saved.getDownloadUrl(), saved.getReleaseNotes());
        return saved;
    }
}
