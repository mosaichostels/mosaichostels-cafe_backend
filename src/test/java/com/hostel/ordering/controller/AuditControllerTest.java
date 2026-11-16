package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditControllerTest {

    @Test
    void actionsComeFromService() {
        AuditService service = mock(AuditService.class);
        when(service.getActions()).thenReturn(List.of("A", "B"));
        AuditController controller = new AuditController();
        controller.auditService = service;

        assertEquals(List.of("A", "B"), controller.getActions());
    }
}
