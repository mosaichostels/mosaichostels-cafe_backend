package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.pms.dto.RoomStatusWriteRequest;
import com.hostel.ordering.pms.dto.RoomStatusWriteResult;
import com.hostel.ordering.service.AuditService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class PmsRoomStatusWriteServiceTest {

    private static final String UPDATE = "UpdateRoomStatus";
    private static final String INHOUSE = "InhouseRoomStatus";
    private static final String OK = "{\"status\":\"0\",\"message\":\"Success\"}";

    private final EzeeGateway gateway = mock(EzeeGateway.class);
    private final AuditService audit = mock(AuditService.class);
    private final PmsProperties props = new PmsProperties();
    private final PmsCache cache = new PmsCache();
    private PmsWriteGuard guard;
    private PmsRoomStatusWriteService service;

    @BeforeEach
    void setUp() {
        props.getWrites().put("room-status", true);
        PmsFlags flags = new PmsFlags(props);
        guard = new PmsWriteGuard(flags, new InMemoryPmsWriteClaimStore(), audit, new ObjectMapper());
        service = new PmsRoomStatusWriteService(new PmsHousekeepingFeed(gateway, cache, props), gateway, flags, guard,
                "H1");
        when(gateway.json(eq(EzeeFamily.HOUSEKEEPING), eq(UPDATE), any(), eq(PmsPriority.HUB)))
                .thenReturn(PmsFrontDeskServiceTest.json(OK));
    }

    private static JsonNode feed(String hk, String blocked) {
        return PmsFrontDeskServiceTest.json("{\"roomlist\":[{\"roomid\":\"11\",\"unitid\":\"21\",\"roomname\":\"A1\","
                + "\"isblocked\":\"" + blocked + "\",\"hkstatus\":\"" + hk + "\",\"hkremarks\":\"\"},"
                + "{\"roomid\":\"12\",\"unitid\":\"22\",\"roomname\":\"A2\",\"isblocked\":\"No\",\"hkstatus\":\"Dirty\"}],"
                + "\"checkinguestlist\":[{\"guestname\":\"Asha Rao\",\"email\":\"q@guest.test\",\"room\":\"A1\","
                + "\"bookingstatus\":\"Stayover\"}]}");
    }

    private void inhouse(JsonNode first, JsonNode... more) {
        when(gateway.json(eq(EzeeFamily.HOUSEKEEPING), eq(INHOUSE), any(), eq(PmsPriority.HUB)))
                .thenReturn(first, more);
    }

    private static RoomStatusWriteRequest req(String unit, String status, String remarks) {
        return new RoomStatusWriteRequest(unit, status, remarks);
    }

    private PmsWriteResult<RoomStatusWriteResult> clean(String key) {
        return service.setStatus("11", req("21", "CLEAN", null), key);
    }

    private void updates(int n) {
        verify(gateway, times(n)).json(eq(EzeeFamily.HOUSEKEEPING), eq(UPDATE), any(), any());
    }

    @Test
    void flagOffMakesZeroEzeeCalls() {
        props.getWrites().clear();
        assertThrows(PmsFeatureDisabledException.class, () -> clean("k1"));
        verify(gateway, times(0)).json(any(), any(), any(), any());
    }

    @Test
    void canaryExcludingTheRoomMakesZeroEzeeCalls() {
        props.getCanary().put("room-status", List.of("99"));
        assertThrows(PmsFeatureDisabledException.class, () -> clean("k1"));
        verify(gateway, times(0)).json(any(), any(), any(), any());
    }

    @Test
    void missingOrBadKeyIs400WithoutEzee() {
        for (String key : new String[] {null, "", "  ", "a b", "x+y="}) {
            assertThrows(IllegalArgumentException.class, () -> clean(key));
        }
        verify(gateway, times(0)).json(any(), any(), any(), any());
    }

    @Test
    void invalidInputsAre400WithoutEzee() {
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("1x", req("21", "CLEAN", null), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req("u", "CLEAN", null), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req(null, "CLEAN", null), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req("21", "clean", null), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req("21", "BLOCKED", null), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req("21", null, null), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req("21", "CLEAN", "x".repeat(201)), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", req("21", "CLEAN", "a\nb"), "k"));
        assertThrows(IllegalArgumentException.class, () -> service.setStatus("11", null, "k"));
        verify(gateway, times(0)).json(any(), any(), any(), any());
    }

    @Test
    void staleOrMismatchedUnitIsRejectedWithoutWrite() {
        inhouse(feed("Dirty", "No"));
        assertThrows(ResponseStatusException.class, () -> service.setStatus("11", req("22", "CLEAN", null), "k1"));
        assertThrows(ResponseStatusException.class, () -> service.setStatus("77", req("21", "CLEAN", null), "k2"));
        updates(0);
    }

    @Test
    void sameStatusIsAConfirmedNoOpWithoutEzeeWrite() {
        inhouse(feed(" clean ", "No"));
        PmsWriteResult<RoomStatusWriteResult> r = clean("k1");
        assertTrue(r.confirmed());
        assertEquals("CLEAN", r.value().status());
        updates(0);
    }

    @Test
    void writeWithMatchingReadBackIsConfirmedAndSendsDocBody() {
        JsonNode warm = feed("Dirty", "No");
        inhouse(warm, warm, feed("Clean", "No"));
        feedWarmUp();
        PmsWriteResult<RoomStatusWriteResult> r = service.setStatus("11", req("21", "CLEAN", " Mopped "), "k1");
        assertTrue(r.confirmed());
        assertEquals(new RoomStatusWriteResult("11", "21", "CLEAN", "Clean", true), r.value());
        // warm-up + pre-check + read-back: the cached reply was dropped each time
        verify(gateway, times(3)).json(eq(EzeeFamily.HOUSEKEEPING), eq(INHOUSE), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(gateway).json(eq(EzeeFamily.HOUSEKEEPING), eq(UPDATE), params.capture(), eq(PmsPriority.HUB));
        assertEquals(Map.of("updatehkdata", List.of(Map.of("hotel_code", "H1", "roomid", "11", "unitid", "21",
                "hkstatus", "Clean", "hkremarks", "Mopped"))), params.getValue());
        assertTrue(guard.unconfirmed().isEmpty());
    }

    private void feedWarmUp() {
        new PmsHousekeepingFeed(gateway, cache, props).inhouseStatus();
    }

    @Test
    void writeWithMismatchedReadBackIsUnconfirmedAndNeverRetried() {
        inhouse(feed("Dirty", "No"));
        PmsWriteResult<RoomStatusWriteResult> r = clean("k1");
        assertFalse(r.confirmed());
        assertEquals("Dirty", r.value().hkStatus());
        assertFalse(r.value().confirmed());
        assertEquals(1, guard.unconfirmed().size());
        assertThrows(PmsUnconfirmedException.class, () -> clean("k1"));
        assertThrows(PmsTargetBusyException.class, () -> clean("k2"));
        updates(1);
    }

    @Test
    void timeoutAfterSendIsUnconfirmedAndNotRetried() {
        inhouse(feed("Dirty", "No"));
        when(gateway.json(eq(EzeeFamily.HOUSEKEEPING), eq(UPDATE), any(), any()))
                .thenThrow(new PmsUpstreamException("PMS request failed"));
        assertThrows(PmsUnconfirmedException.class, () -> clean("k1"));
        assertEquals(1, guard.unconfirmed().size());
        assertThrows(PmsUnconfirmedException.class, () -> clean("k1"));
        updates(1);
    }

    @Test
    void errorBodyIsNeverReportedAsSuccess() {
        inhouse(feed("Dirty", "No"));
        when(gateway.json(eq(EzeeFamily.HOUSEKEEPING), eq(UPDATE), any(), any())).thenReturn(
                PmsFrontDeskServiceTest.json("{\"errorcode\":\"1\",\"message\":\"Unauthorized Request\"}"));
        assertThrows(PmsUnconfirmedException.class, () -> clean("k1"));
        updates(1);
    }

    @Test
    void definiteRefusalBeforeSendFreesTheTarget() {
        inhouse(feed("Dirty", "No"));
        when(gateway.json(eq(EzeeFamily.HOUSEKEEPING), eq(UPDATE), any(), any()))
                .thenThrow(PmsUpstreamException.notSent("PMS integration disabled"));
        assertThrows(PmsUpstreamException.class, () -> clean("k1"));
        assertTrue(guard.unconfirmed().isEmpty());
    }

    @Test
    void duplicateKeyReplaysFirstResultWithOneWrite() {
        JsonNode dirty = feed("Dirty", "No");
        inhouse(dirty, feed("Clean", "No"), dirty); // third read is stale: the guard, not the no-op, answers
        PmsWriteResult<RoomStatusWriteResult> first = clean("k1");
        PmsWriteResult<RoomStatusWriteResult> second = clean("k1");
        assertTrue(first.confirmed() && second.confirmed());
        assertEquals(first.value(), second.value());
        updates(1);
    }

    @Test
    void sameKeyDifferentPayloadIsAMismatch() {
        JsonNode dirty = feed("Dirty", "No");
        inhouse(dirty, feed("Clean", "No"), dirty);
        service.setStatus("11", req("21", "CLEAN", "a"), "k1");
        assertThrows(PmsIdempotencyMismatchException.class,
                () -> service.setStatus("11", req("21", "CLEAN", "b"), "k1"));
        updates(1);
    }

    @Test
    void blockedRoomMayStillBeMarked() {
        inhouse(feed("Dirty", "Yes"), feed("Clean", "Yes"));
        assertTrue(clean("k1").confirmed());
        updates(1);
    }

    @Test
    void auditAndResultCarryIdsOnly() {
        inhouse(feed("Dirty", "No"), feed("Clean", "No"));
        PmsWriteResult<RoomStatusWriteResult> r = service.setStatus("11", req("21", "CLEAN", "secret remark"), "k1");
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit, atLeastOnce()).logAction(action.capture(), details.capture());
        assertEquals("PMS_ROOM_STATUS", action.getValue());
        assertEquals("roomId=11 unitId=21 from=DIRTY to=CLEAN remarksLength=13", details.getValue());
        String all = details.getValue() + r;
        for (String leak : new String[] {"Asha", "Rao", "guest.test", "secret"}) {
            assertFalse(all.contains(leak), leak);
        }
    }
}
