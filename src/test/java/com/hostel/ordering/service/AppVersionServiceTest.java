package com.hostel.ordering.service;

import com.hostel.ordering.model.AppVersion;
import com.hostel.ordering.repository.AppVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    @Mock
    AppVersionRepository appVersionRepository;

    @Mock
    FCMNotificationService fcmNotificationService;

    AppVersionService appVersionService;

    @BeforeEach
    void setUp() {
        appVersionService = new AppVersionService(appVersionRepository, fcmNotificationService);
    }

    @Test
    void getLatest_returnsNull_whenNothingPublished() {
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.empty());

        assertNull(appVersionService.getLatest());
    }

    @Test
    void getLatest_returnsStoredVersion() {
        AppVersion stored = new AppVersion(AppVersion.LATEST_ID, 5, "1.5", "http://x/app.apk", "notes", 123L);
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.of(stored));

        assertEquals(stored, appVersionService.getLatest());
    }

    @Test
    void publish_savesAndNotifies_whenNoExistingVersion() {
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.empty());
        AppVersion request = new AppVersion(null, 2, "1.1", "http://x/app.apk", "fix", 0L);
        when(appVersionRepository.save(any(AppVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        AppVersion result = appVersionService.publish(request);

        assertEquals(AppVersion.LATEST_ID, result.getId());
        assertEquals(2, result.getVersionCode());
        assertTrue(result.getPublishedAt() > 0);

        ArgumentCaptor<AppVersion> captor = ArgumentCaptor.forClass(AppVersion.class);
        verify(appVersionRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getVersionCode());

        verify(fcmNotificationService).sendAppUpdateNotification("1.1", "http://x/app.apk", "fix");
    }

    @Test
    void publish_rejectsNonIncreasingVersionCode() {
        AppVersion existing = new AppVersion(AppVersion.LATEST_ID, 5, "1.5", "http://x/app.apk", "notes", 123L);
        when(appVersionRepository.findById(AppVersion.LATEST_ID)).thenReturn(Optional.of(existing));

        AppVersion request = new AppVersion(null, 5, "1.5-again", "http://x/app.apk", "notes", 0L);

        assertThrows(IllegalArgumentException.class, () -> appVersionService.publish(request));
        verify(appVersionRepository, never()).save(any());
        verify(fcmNotificationService, never()).sendAppUpdateNotification(any(), any(), any());
    }
}
