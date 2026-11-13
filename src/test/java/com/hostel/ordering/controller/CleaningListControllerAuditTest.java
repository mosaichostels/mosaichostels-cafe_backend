package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.CleaningListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleaningListControllerAuditTest {

    @Mock CleaningListService service;
    @Mock AuditService auditService;
    @Mock Authentication authentication;
    CleaningListController controller;

    @BeforeEach
    void setUp() {
        controller = new CleaningListController(service, auditService);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().doThrow(new RuntimeException("mongo down")).when(auditService).logAction(anyString(), anyString());
    }

    @Test
    void doneStillOkWhenAuditFails() {
        when(service.done("b1", "admin")).thenReturn(new CleaningListService.DoneResult(false, "CONFIRMED", "Dorm 1"));
        ResponseEntity<?> res = controller.done("b1", authentication);
        assertEquals(200, res.getStatusCode().value());
        verify(auditService).logAction(eq("CLEANING_BED_DONE"), anyString());
    }

    @Test
    void submitStillOkWhenAuditFails() {
        when(service.submit(anyList(), eq("admin"), any(), any())).thenReturn(new CleaningListService.SubmitResult(1, 0));
        ResponseEntity<?> res = controller.submit(
                Map.of("beds", List.of(Map.of("roomId", "1", "unitId", "1", "roomName", "Dorm 1"))), authentication);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void daySubmitAuditsAndStaysOkWhenAuditFails() {
        when(service.submit(anyList(), eq("admin"), eq("2026-11-13"), eq(4)))
                .thenReturn(new CleaningListService.SubmitResult(0, 0, true));
        ResponseEntity<?> res = controller.submit(Map.of("beds", List.of(), "day", "2026-11-13", "taskCount", 4), authentication);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(true, ((Map<?, ?>) res.getBody()).get("daySubmitted"));
        verify(auditService).logAction(eq("SCHEDULE_DAY_SUBMITTED"), anyString());
    }

    @Test
    void removeAuditsAndStaysNoContentWhenAuditFails() {
        when(service.remove("b1")).thenReturn(true);
        ResponseEntity<?> res = controller.remove("b1", authentication);
        assertEquals(204, res.getStatusCode().value());
        verify(auditService).logAction(eq("CLEANING_BED_REMOVED"), contains("b1"));
    }

    @Test
    void removeOfMissingBedIsNotAudited() {
        when(service.remove("b2")).thenReturn(false);
        assertEquals(404, controller.remove("b2", authentication).getStatusCode().value());
        verifyNoInteractions(auditService);
    }
}
