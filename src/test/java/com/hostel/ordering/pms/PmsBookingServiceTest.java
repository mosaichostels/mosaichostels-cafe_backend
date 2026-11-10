package com.hostel.ordering.pms;

import static com.hostel.ordering.pms.PmsFrontDeskServiceTest.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.NullNode;
import com.hostel.ordering.pms.dto.BookingDetail;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

class PmsBookingServiceTest {

    static final String BOOKING = """
            {"Reservations":{"Reservation":[{"UniqueID":"900","FirstName":"Zed","LastName":"Quill",
              "Phone":"5550101","Mobile":"","Email":"zed.quill@guest.test","Source":"Direct",
              "BookingTran":[
               {"SubBookingId":"9001-1","RoomName":"D1","RoomTypeName":"Dorm","Start":"2026-11-10",
                "End":"2026-11-13","CurrentStatus":"Confirmed","Phone":"","Mobile":"5550102","Email":""},
               {"SubBookingId":"9001-2","RoomName":"D2","RoomTypeName":"Dorm","Start":"2026-11-10",
                "End":"2026-11-11","CurrentStatus":"Confirmed"}]}]}}""";
    static final String BILLS = """
            {"Success":{"FolioList":[{"foliono":"F77","billno":"B1","BillToContact":"x","GuestName":"Zed Quill",
              "CurrencyCode":"INR","TotalCharges":"1500.50","PaidAmount":"500","DueAmount":"1000.50"},
             {"foliono":"F78","CurrencyCode":"INR","TotalCharges":"","PaidAmount":"n/a","DueAmount":"0.10"}]},
             "Errors":{"ErrorCode":"0"}}""";

    EzeeGateway gateway;
    PmsBookingService service;
    AtomicLong clock = new AtomicLong(1_000);

    @BeforeEach
    void setUp() throws Exception {
        gateway = mock(EzeeGateway.class);
        PmsProperties props = new PmsProperties();
        service = new PmsBookingService(gateway, new PmsCache(clock::get), props);
        when(gateway.json(eq(EzeeFamily.CONNECTIVITY), eq("FetchSingleBooking"), any(), any())).thenReturn(json(BOOKING));
        when(gateway.json(eq(EzeeFamily.KIOSK), eq("RetrieveListofBills"), any(), any())).thenReturn(json(BILLS));
    }

    @Test
    void mergesBookingAndFoliosAndPicksTheRequestedTran() throws Exception {
        Cached<BookingDetail> c = service.detail("9001-2");
        BookingDetail d = c.value();
        assertEquals("9001-2", d.summary().bookingId());
        assertEquals("Zed Quill", d.summary().guestName());
        assertEquals("D2", d.summary().room());
        assertEquals(1, d.summary().nights());
        assertEquals(2, d.folios().size());
        assertEquals(new BigDecimal("1500.50"), d.folios().get(0).total());
        assertEquals(new BigDecimal("1000.50"), d.folios().get(0).due());
        assertEquals(BigDecimal.ZERO, d.folios().get(1).total());
        assertNull(d.folios().get(1).paid());
        assertFalse(c.stale());
        verify(gateway).json(EzeeFamily.CONNECTIVITY, "FetchSingleBooking", Map.of("BookingId", "9001-2"),
                PmsPriority.HUB);
        verify(gateway).json(EzeeFamily.KIOSK, "RetrieveListofBills",
                Map.of("Authentication", Map.of("BookingId", "9001-2")), PmsPriority.HUB);
    }

    @Test
    void unknownTranFallsBackToFirstAndContactPrefersMobile() {
        BookingDetail d = service.detail("9001").value();
        assertEquals("9001-1", d.summary().bookingId());
        assertEquals("5550102", d.phone());
        assertEquals("zed.quill@guest.test", d.email());
    }

    @Test
    void reservationLevelPhoneIsTheFallback() {
        BookingDetail d = service.detail("9001-2").value();
        assertEquals("5550101", d.phone());
    }

    @Test
    void missingOrErrorFolioReplyGivesEmptyFolios() throws Exception {
        for (String reply : new String[] {"{\"Errors\":{\"ErrorCode\":\"5\"}}", "{\"Success\":{\"FolioList\":[]}}",
                "{\"Success\":{}}"}) {
            service = new PmsBookingService(gateway, new PmsCache(clock::get), new PmsProperties());
            when(gateway.json(eq(EzeeFamily.KIOSK), any(), any(), any())).thenReturn(json(reply));
            assertEquals(0, service.detail("9001-1").value().folios().size());
        }
        when(gateway.json(eq(EzeeFamily.KIOSK), any(), any(), any())).thenReturn(NullNode.getInstance());
        service = new PmsBookingService(gateway, new PmsCache(clock::get), new PmsProperties());
        assertEquals(0, service.detail("9001-1").value().folios().size());
    }

    @Test
    void failingFolioCallDoesNotFailDetailAndIsNotCached() throws Exception {
        when(gateway.json(eq(EzeeFamily.KIOSK), any(), any(), any())).thenThrow(new PmsUpstreamException("down"));
        Cached<BookingDetail> down = service.detail("9001-1");
        assertEquals(0, down.value().folios().size());
        assertTrue(down.stale());
        when(gateway.json(eq(EzeeFamily.KIOSK), any(), any(), any())).thenReturn(json(BILLS));
        Cached<BookingDetail> up = service.detail("9001-1");
        assertEquals(2, up.value().folios().size());
        assertFalse(up.stale());
    }

    @Test
    void thousandsSeparatorsAreAccepted() throws Exception {
        when(gateway.json(eq(EzeeFamily.KIOSK), any(), any(), any())).thenReturn(json(
                "{\"Success\":{\"FolioList\":[{\"foliono\":\"F1\",\"TotalCharges\":\"1,234.50\"}]}}"));
        assertEquals(new BigDecimal("1234.50"), service.detail("9001-1").value().folios().get(0).total());
    }

    @Test
    void explicitSubBookingIdMatchingNoTranFallsBackToFirst() {
        assertEquals("9001-1", service.detail("9001-9").value().summary().bookingId());
    }

    @Test
    void emptyBookingReplyIsNotFound() throws Exception {
        for (var reply : new com.fasterxml.jackson.databind.JsonNode[] {NullNode.getInstance(),
                json("{\"Reservations\":{\"Reservation\":[]}}"), json("{}")}) {
            service = new PmsBookingService(gateway, new PmsCache(clock::get), new PmsProperties());
            when(gateway.json(eq(EzeeFamily.CONNECTIVITY), any(), any(), any())).thenReturn(reply);
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.detail("9001"));
            assertEquals(404, e.getStatusCode().value());
        }
    }

    @Test
    void invalidIdsNeverReachEzee() {
        for (String bad : new String[] {null, "", "  ", "abc", "12a", "1".repeat(21), "1-2345", "1; DROP", "1-1-1",
                "../1", "1\n2"}) {
            assertThrows(IllegalArgumentException.class, () -> service.detail(bad), String.valueOf(bad));
        }
        verifyNoInteractions(gateway);
    }

    @Test
    void secondCallIsCachedAndUpstreamFailureServesStale() throws Exception {
        service.detail("9001-1");
        service.detail("9001-1");
        verify(gateway, times(1)).json(eq(EzeeFamily.CONNECTIVITY), any(), any(), any());
        clock.addAndGet(Duration.ofSeconds(31).toMillis());
        when(gateway.json(eq(EzeeFamily.CONNECTIVITY), any(), any(), any())).thenThrow(new PmsUpstreamException("x"));
        Cached<BookingDetail> c = service.detail("9001-1");
        assertTrue(c.stale());
        assertEquals(1_000, c.fetchedAtMs());
        assertEquals("9001-1", c.value().summary().bookingId());
    }

    @Test
    void upstreamFailureWithoutCachePropagates() throws Exception {
        when(gateway.json(eq(EzeeFamily.CONNECTIVITY), any(), any(), any())).thenThrow(new PmsUpstreamException("x"));
        assertThrows(PmsUpstreamException.class, () -> service.detail("9001-1"));
        verify(gateway, never()).json(eq(EzeeFamily.KIOSK), any(), any(), any());
    }

    @Test
    void noPersonalDataReachesAnyLog() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level old = root.getLevel();
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        root.setLevel(Level.ALL);
        root.addAppender(app);
        try {
            service.detail("9001-1");
            clock.addAndGet(Duration.ofSeconds(31).toMillis());
            when(gateway.json(eq(EzeeFamily.CONNECTIVITY), any(), any(), any()))
                    .thenThrow(new PmsUpstreamException("x")); // stale path logs at DEBUG
            service.detail("9001-1");
            assertFalse(app.list.isEmpty(), "stale path should have logged something to inspect");
            for (ILoggingEvent e : app.list) {
                String m = e.getFormattedMessage() + e.getThrowableProxy();
                for (String pii : new String[] {"5550101", "5550102", "zed", "Zed", "Quill", "guest.test"}) {
                    assertFalse(m.contains(pii), "log leaked " + pii + ": " + m);
                }
            }
        } finally {
            root.detachAppender(app);
            root.setLevel(old);
        }
    }
}
