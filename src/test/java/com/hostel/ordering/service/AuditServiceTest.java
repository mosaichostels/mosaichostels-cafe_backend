package com.hostel.ordering.service;

import com.hostel.ordering.repository.AuditRepository;
import com.hostel.ordering.model.AuditLog;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditRepository auditRepository;

    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    AuditService auditService;

    @Test
    void deleteAllLogs_callsRepositoryDeleteAll() {
        auditService.deleteAllLogs();

        verify(auditRepository, times(1)).deleteAll();
    }

    @Test
    void filteredLogsUseBoundedSortedLiteralMongoQuery() {
        when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class),
                org.mockito.ArgumentMatchers.eq(AuditLog.class))).thenReturn(List.of());

        auditService.getFilteredLogs("a.*", "u+", 10L, 20L, 5000, 7);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), org.mockito.ArgumentMatchers.eq(AuditLog.class));
        assertEquals(1000, query.getValue().getLimit());
        assertEquals(7, query.getValue().getSkip());
        assertEquals(-1, query.getValue().getSortObject().get("timestamp"));
        Pattern action = (Pattern) query.getValue().getQueryObject().get("action");
        Pattern username = (Pattern) query.getValue().getQueryObject().get("username");
        assertEquals(Pattern.quote("a.*"), action.pattern());
        assertEquals(Pattern.quote("u+"), username.pattern());
        assertTrue((action.flags() & Pattern.CASE_INSENSITIVE) != 0);
        var timestamp = (org.bson.Document) query.getValue().getQueryObject().get("timestamp");
        assertEquals(10L, timestamp.get("$gte"));
        assertEquals(20L, timestamp.get("$lte"));
    }

    @Test
    void actionsAreDistinctAndSorted() {
        when(mongoTemplate.findDistinct(org.mockito.ArgumentMatchers.any(Query.class),
                org.mockito.ArgumentMatchers.eq("action"), org.mockito.ArgumentMatchers.eq(AuditLog.class),
                org.mockito.ArgumentMatchers.eq(String.class))).thenReturn(List.of("Z", "A"));

        assertEquals(List.of("A", "Z"), auditService.getActions());
    }
}
