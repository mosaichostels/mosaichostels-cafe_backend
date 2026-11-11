package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hostel.ordering.pms.dto.CleaningTask;
import com.hostel.ordering.pms.dto.CleaningTaskList;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PmsHousekeepingWorkflowServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC);
    static final String ROOMS = """
            {"roomlist":[
              {"roomid":"11","unitid":"21","roomname":"D1","roomtypename":"Dorm"},
              {"roomid":"12","unitid":"22","roomname":"9O9-9","roomtypename":"Private"}],
             "checkinguestlist":[]}""";

    EzeeGateway gateway;
    PmsHousekeepingWorkflowService service;
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
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(PmsTestJson.json(ROOMS));
        stubDepartures(departures("D1", " d1 ", "9o9-9", "909-9", "ZZ9"));
        service = new PmsHousekeepingWorkflowService(gateway, cache, props, new PmsHousekeepingFeed(gateway, cache, props), CLOCK);
    }

    void stubDepartures(String json) throws Exception {
        when(gateway.json(any(), eq("DepartureList"), any(), any())).thenReturn(PmsTestJson.json(json));
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
    void departureListIsAskedForTheDayOnly() {
        service.tasks("2026-11-10");
        verify(gateway).json(any(), eq("DepartureList"),
                eq(Map.of("Date", Map.of("from_date", "2026-11-10", "to_date", "2026-11-10"))), any());
    }

    @Test
    void badOrFarDatesAreRejected() {
        for (String d : new String[] {"garbage", "10/11/2026", "2026-12-12", "2026-09-01"}) {
            assertThrows(IllegalArgumentException.class, () -> service.tasks(d), d);
        }
        assertEquals(0, service.tasks("2026-12-11").items().size()); // 31 days away is allowed (rows are for 11-10)
    }

    @Test
    void staleFeedIsServedAndAFailureWithNoCachePropagates() throws Exception {
        PmsProperties props = new PmsProperties();
        var clock = new java.util.concurrent.atomic.AtomicLong(1000);
        PmsCache c = new PmsCache(clock::get);
        PmsHousekeepingFeed feed = new PmsHousekeepingFeed(gateway, c, props);
        var svc = new PmsHousekeepingWorkflowService(gateway, c, props, feed, CLOCK);
        assertFalse(svc.tasks("2026-11-10").stale());
        clock.addAndGet(10 * 60_000);
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenThrow(new PmsUpstreamException("x"));
        when(gateway.json(any(), eq("DepartureList"), any(), any())).thenThrow(new PmsUpstreamException("x"));
        assertTrue(svc.tasks("2026-11-10").stale());
        PmsCache fresh = new PmsCache();
        PmsHousekeepingFeed f2 = new PmsHousekeepingFeed(gateway, fresh, props);
        var svc2 = new PmsHousekeepingWorkflowService(gateway, fresh, props, f2, CLOCK);
        assertThrows(PmsUpstreamException.class, () -> svc2.tasks("2026-11-10"));
    }

    @Test
    void duplicateAndBlankRoomNamesAreNotAttributed() throws Exception {
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(PmsTestJson.json("""
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
    void hkStatusComesFromTheRoomsHkstatusByRoomAndUnit() throws Exception {
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(PmsTestJson.json("""
                {"roomlist":[
                  {"roomid":"11","unitid":"21","roomname":"D1","roomtypename":"Dorm","hkstatus":"Clean"},
                  {"roomid":"12","unitid":"22","roomname":"9O9-9","roomtypename":"Private","hkstatus":" Dirty "}],
                 "checkinguestlist":[]}"""));
        CleaningTaskList l = service.tasks("2026-11-10");
        assertEquals("Dirty", l.items().get(0).hkStatus()); // 9O9-9
        assertEquals("Clean", l.items().get(1).hkStatus()); // D1
    }

    @Test
    void hkStatusIsEmptyWhenTheFeedHasNoStatusForTheRoom() {
        assertTrue(service.tasks("2026-11-10").items().stream().allMatch(t -> t.hkStatus().isEmpty()));
    }

    @Test
    void feedFailureServesStaleTasksWithTheirLastStatus() throws Exception {
        PmsProperties props = new PmsProperties();
        var clock = new java.util.concurrent.atomic.AtomicLong(1000);
        PmsCache c = new PmsCache(clock::get);
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(PmsTestJson.json("""
                {"roomlist":[{"roomid":"11","unitid":"21","roomname":"D1","roomtypename":"Dorm","hkstatus":"Clean"}],
                 "checkinguestlist":[]}"""));
        var svc = new PmsHousekeepingWorkflowService(gateway, c, props, new PmsHousekeepingFeed(gateway, c, props), CLOCK);
        svc.tasks("2026-11-10");
        clock.addAndGet(10 * 60_000);
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenThrow(new PmsUpstreamException("x"));
        CleaningTaskList l = svc.tasks("2026-11-10");
        assertTrue(l.stale());
        assertEquals(1, l.items().size());
        assertEquals("Clean", l.items().get(0).hkStatus());
    }

    @Test
    void unmatchedListIsUnchangedByStatus() throws Exception {
        CleaningTaskList l = service.tasks("2026-11-10");
        assertEquals(List.of("909-9", "ZZ9"), l.unmatched().stream().map(CleaningTaskList.Unmatched::roomName).toList());
    }

    @Test
    void aCachedFeedCostsNoExtraGatewayCall() {
        service.tasks("2026-11-10");
        service.tasks("2026-11-10");
        verify(gateway, times(1)).json(any(), eq("InhouseRoomStatus"), any(), any());
    }
}
