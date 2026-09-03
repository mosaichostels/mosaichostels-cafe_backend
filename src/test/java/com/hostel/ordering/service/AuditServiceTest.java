package com.hostel.ordering.service;

import com.hostel.ordering.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditRepository auditRepository;

    @InjectMocks
    AuditService auditService;

    @Test
    void deleteAllLogs_callsRepositoryDeleteAll() {
        auditService.deleteAllLogs();

        verify(auditRepository, times(1)).deleteAll();
    }
}
