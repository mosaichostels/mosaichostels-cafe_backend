package com.hostel.ordering.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.CleaningNotifier;
import com.hostel.ordering.service.ScheduleService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ScheduleControllerTickPushTest {

    ScheduleService service;
    CleaningNotifier notifier;
    ScheduleController controller;
    Authentication auth;

    @BeforeEach
    void setUp() {
        service = mock(ScheduleService.class);
        notifier = mock(CleaningNotifier.class);
        controller = new ScheduleController(service, mock(AuditService.class), notifier);
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("hk1");
    }

    @Test
    void displayNameCases() {
        assertEquals("Clean washroom", ScheduleController.displayName("  Clean washroom ", "wash"));
        assertEquals("wash", ScheduleController.displayName(null, "wash"));
        assertEquals("wash", ScheduleController.displayName("   ", "wash"));
        assertEquals("wash", ScheduleController.displayName("x".repeat(81), "wash"));
        assertEquals("x".repeat(80), ScheduleController.displayName("x".repeat(80), "wash"));
        assertEquals("wash", ScheduleController.displayName("bad\nlabel", "wash"));
        assertEquals("wash", ScheduleController.displayName("bad\u0007label", "wash"));
    }

    @Test
    void newTickPushesWithLabel() {
        when(service.tick("2026-11-13", "wash", "AM", "hk1")).thenReturn(new ScheduleService.TickResult(false, "hk1"));
        var out = controller.tick("2026-11-13", Map.of("taskKey", "wash", "slot", "AM", "label", "Clean washroom"), auth);
        assertEquals(false, out.get("alreadyTicked"));
        verify(notifier).taskDone("Clean washroom", "hk1", "2026-11-13");
    }

    @Test
    void newTickWithoutLabelFallsBackToKey() {
        when(service.tick(any(), any(), any(), any())).thenReturn(new ScheduleService.TickResult(false, "hk1"));
        controller.tick("2026-11-13", Map.of("taskKey", "wash", "slot", "AM"), auth);
        verify(notifier).taskDone("wash", "hk1", "2026-11-13");
    }

    @Test
    void alreadyTickedSendsNoPush() {
        when(service.tick(any(), any(), any(), any())).thenReturn(new ScheduleService.TickResult(true, "other"));
        controller.tick("2026-11-13", Map.of("taskKey", "wash", "slot", "AM", "label", "L"), auth);
        verifyNoInteractions(notifier);
    }

    @Test
    void notifierFailureDoesNotBreakTick() {
        when(service.tick(any(), any(), any(), any())).thenReturn(new ScheduleService.TickResult(false, "hk1"));
        doThrow(new RuntimeException("boom")).when(notifier).taskDone(any(), any(), any());
        var out = controller.tick("2026-11-13", Map.of("taskKey", "wash", "slot", "AM"), auth);
        assertEquals(false, out.get("alreadyTicked"));
        assertEquals("hk1", out.get("doneBy"));
    }
}
