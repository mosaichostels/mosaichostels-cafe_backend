package com.hostel.ordering.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hostel.ordering.model.ScheduleReminder;
import com.hostel.ordering.pms.PmsDates;
import com.hostel.ordering.repository.ScheduleReminderRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ScheduleReminderTaskTest {

    private ScheduleService schedule;
    private ScheduleReminderRepository reminders;
    private CleaningNotifier notifier;

    @BeforeEach
    void setUp() {
        schedule = mock(ScheduleService.class);
        reminders = mock(ScheduleReminderRepository.class);
        notifier = mock(CleaningNotifier.class);
    }

    private ScheduleReminderTask at(String instant) {
        return new ScheduleReminderTask(schedule, reminders, notifier,
                Clock.fixed(Instant.parse(instant), PmsDates.IST));
    }

    @Test
    void submittedDayDoesNothing() {
        when(schedule.isSubmitted("2026-11-16")).thenReturn(true);
        at("2026-11-16T03:30:00Z").run();
        verifyNoInteractions(reminders, notifier);
    }

    @Test
    void notSubmittedRecordsRowAndPushesOnce() {
        when(schedule.isSubmitted("2026-11-16")).thenReturn(false);
        when(notifier.scheduleNotSubmitted()).thenReturn(1);
        at("2026-11-16T03:30:00Z").run();
        ArgumentCaptor<ScheduleReminder> row = ArgumentCaptor.forClass(ScheduleReminder.class);
        verify(reminders).insert(row.capture());
        assertEquals("2026-11-16", row.getValue().getDate());
        verify(notifier, times(1)).scheduleNotSubmitted();
    }

    @Test
    void duplicateKeyMeansAnotherRunWonAndNoPush() {
        when(schedule.isSubmitted("2026-11-16")).thenReturn(false);
        when(reminders.insert(any(ScheduleReminder.class))).thenThrow(new DuplicateKeyException("dup"));
        at("2026-11-16T03:40:00Z").run();
        verifyNoInteractions(notifier);
    }

    @Test
    void differentDaySendsAgain() {
        when(schedule.isSubmitted(any())).thenReturn(false);
        at("2026-11-16T03:30:00Z").run();
        at("2026-11-17T03:30:00Z").run();
        verify(notifier, times(2)).scheduleNotSubmitted();
    }

    @Test
    void zeroAcceptedPushesDeleteOnlyTheInsertedClaim() {
        when(schedule.isSubmitted("2026-11-16")).thenReturn(false);
        ScheduleReminder inserted = new ScheduleReminder();
        inserted.setId("claim-1");
        when(reminders.insert(any(ScheduleReminder.class))).thenReturn(inserted);
        when(notifier.scheduleNotSubmitted()).thenReturn(0);

        assertDoesNotThrow(() -> at("2026-11-16T03:30:00Z").run());
        verify(reminders).deleteById("claim-1");
    }

    @Test
    void notifierFailureDeletesClaimForRetry() {
        when(schedule.isSubmitted("2026-11-16")).thenReturn(false);
        ScheduleReminder inserted = new ScheduleReminder();
        inserted.setId("claim-2");
        when(reminders.insert(any(ScheduleReminder.class))).thenReturn(inserted);
        when(notifier.scheduleNotSubmitted()).thenThrow(new RuntimeException("fcm"));

        assertDoesNotThrow(() -> at("2026-11-16T03:30:00Z").run());
        verify(reminders).deleteById("claim-2");
    }
}
