package com.hostel.ordering.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hostel.ordering.model.ScheduleDay;
import com.hostel.ordering.model.ScheduleTick;
import com.hostel.ordering.pms.PmsDates;
import com.hostel.ordering.repository.ScheduleDayRepository;
import com.hostel.ordering.repository.ScheduleTickRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

class ScheduleServiceTest {

    private static final String TODAY = "2026-11-16";

    private ScheduleDayRepository days;
    private ScheduleTickRepository ticks;
    private MongoTemplate mongo;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        days = mock(ScheduleDayRepository.class);
        ticks = mock(ScheduleTickRepository.class);
        mongo = mock(MongoTemplate.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T05:00:00Z"), PmsDates.IST);
        service = new ScheduleService(days, ticks, mongo, clock);
    }

    private void submitted(String date) {
        when(days.existsByDate(date)).thenReturn(true);
    }

    private static ScheduleTick tickOf(String doneBy) {
        ScheduleTick t = new ScheduleTick();
        t.setDoneBy(doneBy);
        return t;
    }

    @Test
    void markSubmittedTodayThenRepeat() {
        assertTrue(service.markSubmitted(TODAY, "admin"));
        verify(days).save(any(ScheduleDay.class));
        when(days.existsByDate(TODAY)).thenReturn(true);
        assertFalse(service.markSubmitted(TODAY, "admin"));
        verify(days, times(1)).save(any(ScheduleDay.class));
    }

    @Test
    void markSubmittedRaceLosesQuietly() {
        when(days.save(any())).thenThrow(new DuplicateKeyException("dup"));
        assertFalse(service.markSubmitted(TODAY, "admin"));
    }

    @Test
    void markSubmittedRejectsPastFutureAndMalformed() {
        assertThrows(IllegalArgumentException.class, () -> service.markSubmitted("2026-11-15", "a"));
        assertThrows(IllegalArgumentException.class, () -> service.markSubmitted("2026-11-17", "a"));
        assertThrows(IllegalArgumentException.class, () -> service.markSubmitted("16-11-2026", "a"));
        verify(days, never()).save(any());
    }

    @Test
    void tickRejectsUnsubmittedFutureAndTooOld() {
        assertThrows(IllegalArgumentException.class, () -> service.tick(TODAY, "k", "ONCE", "h"));
        submitted("2026-11-17");
        submitted("2026-10-15");
        assertThrows(IllegalArgumentException.class, () -> service.tick("2026-11-17", "k", "ONCE", "h"));
        assertThrows(IllegalArgumentException.class, () -> service.tick("2026-10-15", "k", "ONCE", "h"));
        verify(ticks, never()).save(any());
    }

    @Test
    void tickRejectsBadKeyAndSlot() {
        submitted(TODAY);
        for (String key : new String[] {"Bad Key", "", null, "a".repeat(61)}) {
            assertThrows(IllegalArgumentException.class, () -> service.tick(TODAY, key, "ONCE", "h"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.tick(TODAY, "k", "NOON", "h"));
        assertThrows(IllegalArgumentException.class, () -> service.tick(TODAY, "k", null, "h"));
    }

    @Test
    void tickSavesAndAcceptsBoundaryDates() {
        submitted(TODAY);
        submitted("2026-11-15");
        submitted("2026-10-16");
        assertEquals(new ScheduleService.TickResult(false, "h"), service.tick(TODAY, "k", "AM", "h"));
        assertFalse(service.tick("2026-11-15", "k", "ONCE", "h").alreadyTicked());
        assertFalse(service.tick("2026-10-16", "k", "ONCE", "h").alreadyTicked());
        verify(ticks, times(3)).save(any(ScheduleTick.class));
    }

    @Test
    void duplicateTickReturnsFirstPersonAndSavesNothing() {
        submitted(TODAY);
        when(ticks.findByDateAndTaskKeyAndSlot(TODAY, "k", "ONCE")).thenReturn(Optional.of(tickOf("first")));
        var r = service.tick(TODAY, "k", "ONCE", "second");
        assertTrue(r.alreadyTicked());
        assertEquals("first", r.doneBy());
        verify(ticks, never()).save(any());
    }

    @Test
    void duplicateKeyRaceReturnsWinner() {
        submitted(TODAY);
        when(ticks.findByDateAndTaskKeyAndSlot(TODAY, "k", "ONCE"))
                .thenReturn(Optional.empty(), Optional.of(tickOf("winner")));
        when(ticks.save(any())).thenThrow(new DuplicateKeyException("dup"));
        var r = service.tick(TODAY, "k", "ONCE", "loser");
        assertTrue(r.alreadyTicked());
        assertEquals("winner", r.doneBy());
    }

    @Test
    void confirmMissingTickIsFalseAndPastDateRejected() {
        assertFalse(service.confirm(TODAY, "k", "ONCE", "admin"));
        verify(mongo, never()).updateFirst(any(), any(), eq(ScheduleTick.class));
        assertThrows(IllegalArgumentException.class, () -> service.confirm("2026-11-15", "k", "ONCE", "admin"));
    }

    @Test
    void confirmExistingTickUpdatesOnlyUnconfirmed() {
        when(ticks.findByDateAndTaskKeyAndSlot(TODAY, "k", "ONCE")).thenReturn(Optional.of(tickOf("h")));
        assertTrue(service.confirm(TODAY, "k", "ONCE", "admin"));
        assertTrue(service.confirm(TODAY, "k", "ONCE", "admin"));
        verify(mongo, times(2)).updateFirst(any(), any(), eq(ScheduleTick.class));
    }

    @Test
    void daysRangeLimitAndSubmittedOnly() {
        assertThrows(IllegalArgumentException.class, () -> service.days("2026-10-15", "2026-11-16")); // 33 days
        assertThrows(IllegalArgumentException.class, () -> service.days("2026-11-16", "2026-11-15"));
        assertDoesNotThrow(() -> service.days("2026-10-16", "2026-11-16")); // 32 days

        ScheduleDay d = new ScheduleDay();
        d.setDate("2026-11-15");
        d.setSubmittedAt(42L);
        ScheduleTick t = tickOf("h");
        t.setDate("2026-11-15");
        when(days.findByDateBetweenOrderByDate("2026-11-14", "2026-11-16")).thenReturn(List.of(d));
        when(ticks.findByDateBetween("2026-11-14", "2026-11-16")).thenReturn(List.of(t));
        var out = service.days("2026-11-14", "2026-11-16");
        assertEquals(1, out.size());
        assertEquals("2026-11-15", out.get(0).date());
        assertTrue(out.get(0).submitted());
        assertEquals(42L, out.get(0).submittedAt());
        assertEquals(List.of(t), out.get(0).ticks());
    }
}
