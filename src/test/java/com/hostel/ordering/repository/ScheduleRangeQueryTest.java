package com.hostel.ordering.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.Query;

/**
 * Pins the range queries inclusive. A derived findByDateBetween is exclusive on Spring Data MongoDB, which hid
 * today from a today-to-today read; no Mongo runs in unit tests, so the query text itself is what we can check.
 */
class ScheduleRangeQueryTest {

    private static String queryOf(Class<?> repo, String method) throws Exception {
        Method m = repo.getMethod(method, String.class, String.class);
        return m.getAnnotation(Query.class).value();
    }

    @Test
    void dayRangeIncludesBothEnds() throws Exception {
        String q = queryOf(ScheduleDayRepository.class, "daysInRange");
        assertTrue(q.contains("$gte") && q.contains("$lte"), q);
        assertTrue(!q.contains("$gt:") && !q.contains("$lt:"), q);
    }

    @Test
    void tickRangeIncludesBothEnds() throws Exception {
        String q = queryOf(ScheduleTickRepository.class, "ticksInRange");
        assertTrue(q.contains("$gte") && q.contains("$lte"), q);
        assertTrue(!q.contains("$gt:") && !q.contains("$lt:"), q);
    }
}
