package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hostel.ordering.model.CleaningAssignment;
import com.hostel.ordering.model.IdempotencyRecord;
import com.hostel.ordering.model.User;
import com.hostel.ordering.pms.dto.CleaningTask;
import com.hostel.ordering.pms.dto.CleaningTaskList;
import com.hostel.ordering.repository.CleaningAssignmentRepository;
import com.hostel.ordering.repository.IdempotencyRepository;
import com.hostel.ordering.repository.UserRepository;
import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.IdempotencyService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

class PmsHousekeepingWorkflowServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC);
    static final String ROOMS = """
            {"roomlist":[
              {"roomid":"11","unitid":"21","roomname":"D1","roomtypename":"Dorm"},
              {"roomid":"12","unitid":"22","roomname":"9O9-9","roomtypename":"Private"}],
             "checkinguestlist":[]}""";

    EzeeGateway gateway;
    Map<String, CleaningAssignment> store;
    Map<String, IdempotencyRecord> idem;
    CleaningAssignmentRepository repo;
    AuditService audit;
    PmsHousekeepingWorkflowService service;
    AtomicInteger saves;
    PmsCache cache;

    static String departures(String... rooms) {
        StringBuilder sb = new StringBuilder("{\"Reservations\":{\"Reservation\":[");
        for (int i = 0; i < rooms.length; i++) {
            sb.append(i > 0 ? "," : "").append("{\"FirstName\":\"Asha\",\"LastName\":\"Rao\",\"Phone\":\"5550001\","
                    + "\"Email\":\"asha@guest.test\",\"Source\":\"Direct\",\"BookingTran\":[{\"SubBookingId\":\"")
                    .append(9000 + i).append("-1\",\"RoomName\":\"").append(rooms[i])
                    .append("\",\"RoomTypeName\":\"X\",\"Start\":\"2026-11-08\",\"End\":\"2026-11-10\","
                            + "\"CurrentStatus\":\"Checked In\"}]}");
        }
        return sb.append("]}}").toString();
    }

    @BeforeEach
    void setUp() throws Exception {
        gateway = mock(EzeeGateway.class);
        cache = new PmsCache();
        PmsProperties props = new PmsProperties();
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(PmsFrontDeskServiceTest.json(ROOMS));
        stubDepartures(departures("D1", " d1 ", "9o9-9", "909-9", "ZZ9"));
        store = new HashMap<>();
        saves = new AtomicInteger();
        repo = mock(CleaningAssignmentRepository.class);
        when(repo.findByTaskKey(anyString())).thenAnswer(i -> Optional.ofNullable(
                store.values().stream().filter(a -> a.getTaskKey().equals(i.getArgument(0))).findFirst().orElse(null)));
        when(repo.findByDepartureDate(anyString())).thenAnswer(i -> store.values().stream()
                .filter(a -> a.getDepartureDate().equals(i.getArgument(0))).toList());
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get((String) i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> {
            CleaningAssignment a = i.getArgument(0);
            saves.incrementAndGet();
            if (a.getId() == null) {
                a.setId(String.format("%024x", store.size() + 1));
            }
            a.setVersion(a.getVersion() == null ? 0L : a.getVersion() + 1);
            store.put(a.getId(), a);
            return a;
        });
        idem = new HashMap<>();
        IdempotencyRepository irepo = mock(IdempotencyRepository.class);
        when(irepo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(idem.get((String) i.getArgument(0))));
        when(irepo.save(any())).thenAnswer(i -> {
            IdempotencyRecord r = i.getArgument(0);
            idem.put(r.getId(), r);
            return r;
        });
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername("sam")).thenReturn(Optional.of(new User("sam", "x", java.util.Set.of("ROLE_STAFF"))));
        audit = mock(AuditService.class);
        PmsHousekeepingFeed feed = new PmsHousekeepingFeed(gateway, cache, props);
        service = new PmsHousekeepingWorkflowService(new PmsFrontDeskService(gateway, cache, props, feed), feed, repo,
                users, new IdempotencyService(irepo), audit, CLOCK);
    }

    void stubDepartures(String json) throws Exception {
        when(gateway.json(any(), eq("DepartureList"), any(), any())).thenReturn(PmsFrontDeskServiceTest.json(json));
    }

    @Test
    void twoDeparturesInOneRoomMakeOneTaskWithTurnoversTwo() {
        CleaningTaskList l = service.tasks(null);
        assertEquals(2, l.items().size());
        assertEquals("9O9-9", l.items().get(0).roomName()); // digits sort before letters
        CleaningTask d1 = l.items().get(1);
        assertEquals("D1", d1.roomName());
        assertEquals(2, d1.turnovers());
        assertEquals("2026-11-10|11", d1.taskKey());
        assertEquals("21", d1.unitId());
        assertEquals("UNASSIGNED", d1.status());
        assertNull(d1.assignmentId());
    }

    @Test
    void unmatchedRoomsAreListedAndLetterOIsNotZero() {
        CleaningTaskList l = service.tasks("2026-11-10");
        assertEquals("9O9-9", l.items().get(0).roomName()); // "9o9-9" matched case-insensitively
        List<String> names = l.unmatched().stream().map(CleaningTaskList.Unmatched::roomName).toList();
        assertEquals(List.of("909-9", "ZZ9"), names); // digit zero does not match letter O
        assertTrue(l.unmatched().stream().allMatch(u -> u.count() == 1 && !u.ambiguous()));
        assertFalse(l.toString().toLowerCase().contains("asha"));
    }

    @Test
    void badOrFarDatesAreRejected() {
        for (String d : new String[] {"garbage", "10/11/2026", "2026-12-12", "2026-09-01"}) {
            assertThrows(IllegalArgumentException.class, () -> service.tasks(d), d);
        }
        assertEquals(0, service.tasks("2026-12-11").items().size()); // 31 days away is allowed (rows are for 11-10)
    }

    @Test
    void storedAssignmentsMergeIntoTheTask() {
        CleaningAssignment a = service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", "dorm"), "k1");
        CleaningTask t = service.tasks("2026-11-10").items().get(1);
        assertEquals("sam", t.assigneeUsername());
        assertEquals("ASSIGNED", t.status());
        assertEquals(a.getId(), t.assignmentId());
        assertEquals(2, t.turnovers());
        assertEquals(0L, t.version());
    }

    @Test
    void assignPersistsNoGuestOrBookingDataAndAuditsIdsOnly() {
        CleaningAssignment a = service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", "strip beds"), "k1");
        String dump = a.getTaskKey() + a.getRoomId() + a.getUnitId() + a.getRoomName() + a.getRoomType()
                + a.getDepartureDate() + a.getAssigneeUsername() + a.getNotes() + a.getStatus();
        for (String banned : new String[] {"Asha", "Rao", "5550", "guest.test", "9000"}) {
            assertFalse(dump.contains(banned), banned);
        }
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit).logAction(eq("CLEANING_ASSIGN"), details.capture());
        assertEquals("assignmentId=" + a.getId() + " taskKey=2026-11-10|11 assignee=sam", details.getValue());
        verify(gateway, never()).json(any(), eq("UpdateRoomStatus"), any(), any());
    }

    @Test
    void sameKeySamePayloadReplaysWithoutASecondWrite() {
        var req = new PmsHousekeepingWorkflowService.AssignRequest("sam", null);
        CleaningAssignment first = service.assign("2026-11-10|11", req, "k1");
        CleaningAssignment again = service.assign("2026-11-10|11", req, "k1");
        assertEquals(first.getId(), again.getId());
        assertEquals(1, saves.get());
        verify(audit, times(1)).logAction(anyString(), anyString());
    }

    @Test
    void sameKeyDifferentPayloadIsAMismatch() {
        service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", null), "k1");
        assertThrows(PmsIdempotencyMismatchException.class, () -> service.assign("2026-11-10|12",
                new PmsHousekeepingWorkflowService.AssignRequest("sam", null), "k1"));
        assertEquals(1, saves.get());
    }

    @Test
    void missingOrBadKeyIs400AndNothingIsWritten() {
        var req = new PmsHousekeepingWorkflowService.AssignRequest("sam", null);
        for (String k : new String[] {null, "", " ", "a b", "x".repeat(129)}) {
            assertThrows(IllegalArgumentException.class, () -> service.assign("2026-11-10|11", req, k));
        }
        assertEquals(0, saves.get());
    }

    @Test
    void taskKeysAndAssigneesAreValidated() {
        var ok = new PmsHousekeepingWorkflowService.AssignRequest("sam", null);
        for (String bad : new String[] {"2026-11-10|1x", "2026-11-10|{\"$ne\":1}", "2026-11-10", "x|11", "2026-11-10|11|1",
                "2026-11-10|", "2027-01-01|11", "2026-11-10|99"}) {
            assertThrows(IllegalArgumentException.class, () -> service.assign(bad, ok, "k" + bad.hashCode()), bad);
        }
        for (String who : new String[] {null, "", "ghost", "x".repeat(101)}) {
            assertThrows(IllegalArgumentException.class, () -> service.assign("2026-11-10|11",
                    new PmsHousekeepingWorkflowService.AssignRequest(who, null), "kw" + who));
        }
        assertEquals(0, saves.get());
    }

    @Test
    void notesLimitIs300() {
        assertNotNull(service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", "n".repeat(300)), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.assign("2026-11-10|12",
                new PmsHousekeepingWorkflowService.AssignRequest("sam", "n".repeat(301)), "b"));
    }

    @Test
    void updateChangesStatusAndRejectsBadInput() {
        CleaningAssignment a = service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", null), "k1");
        CleaningAssignment u = service.update(a.getId(), new PmsHousekeepingWorkflowService.UpdateRequest("in_progress", "x", null, 0L), "k2");
        assertEquals(CleaningAssignment.Status.IN_PROGRESS, u.getStatus());
        assertEquals(CleaningAssignment.Status.DONE, service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, 1L), "k3").getStatus());
        assertEquals(CleaningAssignment.Status.ASSIGNED, service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("ASSIGNED", null, null, 2L), "k4").getStatus());
        var empty = new PmsHousekeepingWorkflowService.UpdateRequest(null, null, null, 3L);
        assertThrows(IllegalArgumentException.class, () -> service.update(a.getId(), empty, "k5"));
        assertThrows(IllegalArgumentException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("NOPE", null, null, 3L), "k6"));
        assertThrows(IllegalArgumentException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest(null, "n".repeat(301), null, 3L), "k7"));
        assertThrows(IllegalArgumentException.class, () -> service.update("../x", empty, "k8"));
        assertThrows(IllegalArgumentException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest(null, null, "ghost", 3L), "k9"));
        assertThrows(ResponseStatusException.class, () -> service.update("f".repeat(24),
                new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, 0L), "k10"));
        CleaningAssignment cleared = service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("UNASSIGNED", null, null, 3L), "k11");
        assertNull(cleared.getAssigneeUsername());
        assertThrows(IllegalArgumentException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, 4L), "k12"));
    }

    @Test
    void staleVersionAndRacingSaveAreConflicts() {
        CleaningAssignment a = service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", null), "k1");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, 99L), "k2"));
        assertEquals(409, e.getStatusCode().value());
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("race")).when(repo).save(any());
        e = assertThrows(ResponseStatusException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, 1L), "k3"));
        assertEquals(409, e.getStatusCode().value());
    }

    @Test
    void staleFeedIsServedAndAFailureWithNoCachePropagates() throws Exception {
        PmsProperties props = new PmsProperties();
        var clock = new java.util.concurrent.atomic.AtomicLong(1000);
        PmsCache c = new PmsCache(clock::get);
        PmsHousekeepingFeed feed = new PmsHousekeepingFeed(gateway, c, props);
        var svc = new PmsHousekeepingWorkflowService(new PmsFrontDeskService(gateway, c, props, feed), feed, repo,
                mock(UserRepository.class), mock(IdempotencyService.class), audit, CLOCK);
        assertFalse(svc.tasks("2026-11-10").stale());
        clock.addAndGet(10 * 60_000);
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenThrow(new PmsUpstreamException("x"));
        when(gateway.json(any(), eq("DepartureList"), any(), any())).thenThrow(new PmsUpstreamException("x"));
        assertTrue(svc.tasks("2026-11-10").stale());
        PmsCache fresh = new PmsCache();
        PmsHousekeepingFeed f2 = new PmsHousekeepingFeed(gateway, fresh, props);
        var svc2 = new PmsHousekeepingWorkflowService(new PmsFrontDeskService(gateway, fresh, props, f2), f2, repo,
                mock(UserRepository.class), mock(IdempotencyService.class), audit, CLOCK);
        assertThrows(PmsUpstreamException.class, () -> svc2.tasks("2026-11-10"));
    }

    @Test
    void duplicateAndBlankRoomNamesAreNotAttributed() throws Exception {
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(PmsFrontDeskServiceTest.json("""
                {"roomlist":[{"roomid":"31","unitid":"1","roomname":"Bunk","roomtypename":"Dorm"},
                 {"roomid":"32","unitid":"2","roomname":" bunk ","roomtypename":"Dorm"},
                 {"roomid":"33","unitid":"3","roomname":"","roomtypename":"X"},
                 {"roomid":"34","unitid":"4","roomname":"   ","roomtypename":"X"},
                 {"roomid":"35","unitid":"5","roomname":"Solo","roomtypename":"X"}],"checkinguestlist":[]}"""));
        stubDepartures(departures("BUNK", "", " ", "Solo"));
        CleaningTaskList l = service.tasks("2026-11-10");
        assertEquals(1, l.items().size());
        assertEquals("35", l.items().get(0).roomId());
        assertEquals(2, l.unmatched().size()); // BUNK and the merged blank
        var bunk = l.unmatched().stream().filter(u -> u.roomName().equals("BUNK")).findFirst().orElseThrow();
        assertTrue(bunk.ambiguous());
        assertEquals(1, bunk.count());
        assertTrue(l.unmatched().stream().filter(u -> u.roomName().isEmpty()).noneMatch(u -> u.ambiguous()));
    }

    @Test
    void assigneeMustBeStaffAndErrorsDoNotRevealUsernames() {
        UserRepository u2 = mock(UserRepository.class);
        when(u2.findByUsername("guestuser")).thenReturn(Optional.of(new User("guestuser", "x", java.util.Set.of("ROLE_GUEST"))));
        when(u2.findByUsername("nobody")).thenReturn(Optional.empty());
        PmsHousekeepingFeed feed = new PmsHousekeepingFeed(gateway, cache, new PmsProperties());
        var svc = new PmsHousekeepingWorkflowService(new PmsFrontDeskService(gateway, cache, new PmsProperties(), feed),
                feed, repo, u2, new IdempotencyService(mock(IdempotencyRepository.class)), audit, CLOCK);
        String m1 = assertThrows(IllegalArgumentException.class, () -> svc.assign("2026-11-10|11",
                new PmsHousekeepingWorkflowService.AssignRequest("guestuser", null), "a")).getMessage();
        String m2 = assertThrows(IllegalArgumentException.class, () -> svc.assign("2026-11-10|11",
                new PmsHousekeepingWorkflowService.AssignRequest("nobody", null), "b")).getMessage();
        assertEquals(m1, m2);
        assertEquals("Assignee is not available", m1);
    }

    @Test
    void idempotencyKeysAreScopedPerCaller() {
        var req = new PmsHousekeepingWorkflowService.AssignRequest("sam", null);
        try {
            as("alice");
            CleaningAssignment a = service.assign("2026-11-10|11", req, "same");
            as("bob");
            service.assign("2026-11-10|12", req, "same"); // not a mismatch and not alice's replay
            assertEquals(2, saves.get());
            as("alice");
            assertEquals(a.getId(), service.assign("2026-11-10|11", req, "same").getId());
            assertEquals(2, saves.get());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateRequiresAVersionAndAssignKeepsDone() {
        CleaningAssignment a = service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", null), "k1");
        assertEquals("version is required", assertThrows(IllegalArgumentException.class, () -> service.update(a.getId(),
                new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, null), "k2")).getMessage());
        service.update(a.getId(), new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, 0L), "k3");
        CleaningAssignment again = service.assign("2026-11-10|11", new PmsHousekeepingWorkflowService.AssignRequest("sam", "n"), "k4");
        assertEquals(CleaningAssignment.Status.DONE, again.getStatus());
    }

    private static void as(String name) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(name, "p", "ROLE_STAFF"));
    }
}
