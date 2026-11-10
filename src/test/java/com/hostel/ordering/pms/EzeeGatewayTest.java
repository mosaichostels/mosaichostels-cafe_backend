package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class EzeeGatewayTest {

    private static final String KEY = "SECRET-AUTH-7f3a91";
    private static final String HOTEL = "H-4242";
    private static final String BASE = "https://pms.test";
    private static final int MB = 1024 * 1024;
    private static final String CSV = "BillNo,Guest,Amount\n101,MARKER-GUEST-Asha,450.00\n";

    /** Records every call and answers with a canned reply; never touches the network. */
    static class FakeTransport implements PmsTransport {
        final List<Call> calls = new ArrayList<>();
        Reply reply = new Reply(200, "{\"Errors\":{\"ErrorCode\":\"0\",\"ErrorMessage\":\"Success\"}}");
        RuntimeException failure;

        record Call(String method, String url, Map<String, String> headers, String body, int maxBytes) {}

        @Override
        public Reply send(String method, String url, Map<String, String> headers, String body, int maxBytes) {
            calls.add(new Call(method, url, headers, body, maxBytes));
            if (failure != null) {
                throw failure;
            }
            return reply;
        }

        Call last() {
            return calls.get(calls.size() - 1);
        }
    }

    private final FakeTransport transport = new FakeTransport();
    private final PmsRateLimiter limiter = new PmsRateLimiter(props());
    private final EzeeGateway gateway = new EzeeGateway(limiter, transport, BASE, KEY, HOTEL);
    private final ObjectMapper mapper = new ObjectMapper();

    private static PmsProperties props() {
        PmsProperties p = new PmsProperties();
        p.setHubPer5s(1000);
        return p;
    }

    private void replyWith(String body) {
        transport.reply = new PmsTransport.Reply(200, body);
    }

    // (a) Review Focus 1: HTTP 200 with an error body is not success.
    @Test
    void http200WithAuthErrorCodeThrowsAuth() {
        replyWith("{\"Errors\":{\"ErrorCode\":\"611\",\"ErrorMessage\":\"Unauthorized Access: Invalid Auth Code or Hotel Code.\"}}");
        assertThrows(PmsAuthException.class,
                () -> gateway.json(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB));
    }

    @Test
    void everyAuthCodeMapsToAuthOnEitherErrorShape() {
        for (String code : List.of("611", "301", "303", "602", "612", "614")) {
            replyWith("{\"Errors\":{\"ErrorCode\":\"" + code + "\"}}");
            assertThrows(PmsAuthException.class,
                    () -> gateway.json(EzeeFamily.CONNECTIVITY, "Bookings", Map.of(), PmsPriority.HUB), code);
        }
        replyWith("{\"status\":\"error\",\"error_code\":\"121\",\"error_message\":\"Blank Authcode\"}");
        assertThrows(PmsAuthException.class,
                () -> gateway.json(EzeeFamily.VACATION_RENTAL, "get_listing", Map.of(), PmsPriority.HUB));
        replyWith("{\"Errors\":{\"ErrorCode\":\"611\"}}");
        assertThrows(PmsAuthException.class,
                () -> gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
    }

    @Test
    void errorCodeZeroIsSuccess() throws Exception {
        String body = "{\"Errors\":{\"ErrorCode\":\"0\",\"ErrorMessage\":\"Success\"},\"Bills\":[1]}";
        replyWith(body);
        assertEquals(mapper.readTree(body),
                gateway.json(EzeeFamily.KIOSK, "RetrieveListofBills", Map.of(), PmsPriority.HUB));
    }

    @Test
    void jsonWithoutErrorMarkerIsSuccess() {
        replyWith("{\"roomlist\":[{\"room\":\"101\"}],\"checkinguestlist\":[]}");
        JsonNode n = gateway.json(EzeeFamily.HOUSEKEEPING, "InhouseRoomStatus", Map.of(), PmsPriority.HUB);
        assertEquals("101", n.path("roomlist").get(0).path("room").asText());
    }

    // (b)
    @Test
    void notEnabledCodesThrowNotEnabled() {
        for (String code : List.of("201", "302")) {
            replyWith("{\"Errors\":{\"ErrorCode\":\"" + code + "\"}}");
            assertThrows(PmsNotEnabledException.class,
                    () -> gateway.json(EzeeFamily.CONNECTIVITY, "Bookings", Map.of(), PmsPriority.HUB), code);
        }
    }

    // (c)
    @Test
    void noDataRepliesAreEmptyNotErrors() {
        replyWith("{\"Errors\":{\"ErrorCode\":\"210\",\"ErrorMessage\":\"No data found\"}}");
        assertTrue(gateway.json(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB).isNull());
        assertEquals("", gateway.text(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB));

        replyWith("{\"0\":{\"Error Details\":{\"Error_Code\":-1,\"Error_Message\":\"No Data found.\"}},\"_ok\":true}");
        assertTrue(gateway.json(EzeeFamily.RESERVATION_GET, "Reservation", Map.of(), PmsPriority.HUB).isNull());

        replyWith("Sorry no records found");
        assertEquals("", gateway.text(EzeeFamily.GUEST_DB, "GuestDatabase", Map.of(), PmsPriority.HUB));
        assertTrue(gateway.json(EzeeFamily.GUEST_DB, "GuestDatabase", Map.of(), PmsPriority.HUB).isNull());
    }

    @Test
    void otherErrorsAreUpstream() {
        replyWith("{\"0\":{\"Error Details\":{\"Error_Code\":\"EmptyParameter\",\"Error_Message\":\"x\"}}}");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.json(EzeeFamily.RESERVATION_GET, "Reservation", Map.of(), PmsPriority.HUB));
        replyWith("{\"Errors\":{\"ErrorCode\":\"999\"}}");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.json(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB));
        replyWith("{\"status\":\"error\",\"error_message\":\"bad\"}");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.json(EzeeFamily.VACATION_RENTAL, "get_listing", Map.of(), PmsPriority.HUB));
    }

    @Test
    void httpStatusesMap() {
        transport.reply = new PmsTransport.Reply(429, "");
        assertThrows(PmsBusyException.class,
                () -> gateway.json(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB));
        transport.reply = new PmsTransport.Reply(500, "{\"Errors\":{\"ErrorCode\":\"0\"}}");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.json(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB));
    }

    @Test
    void transportFailureIsUpstream() {
        transport.failure = new PmsUpstreamException("PMS request failed");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
        transport.failure = new IllegalStateException("boom " + KEY);
        PmsUpstreamException e = assertThrows(PmsUpstreamException.class,
                () -> gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
        assertFalse(e.getMessage().contains(KEY));
        assertNull(e.getCause());
    }

    // (d) Review Focus 2
    @Test
    void csvReturnsThroughTextButJsonFailsWithoutParserMessage() {
        replyWith(CSV);
        assertEquals(CSV, gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
        PmsUpstreamException e = assertThrows(PmsUpstreamException.class,
                () -> gateway.json(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
        assertFalse(e.getMessage().contains("MARKER"));
        assertFalse(e.getMessage().contains("Unrecognized"));
        assertNull(e.getCause());
    }

    @Test
    void pos2pmsXmlStatusDecidesSuccess() {
        String ok = "<?xml version=\"1.0\"?><response><status>ok</status><msg>posted</msg></response>";
        replyWith(ok);
        assertEquals(ok, gateway.text(EzeeFamily.POS2PMS, "postcharge", Map.of(), PmsPriority.CAFE));
        replyWith("<response><status>error</status><msg>Invalid</msg></response>");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.text(EzeeFamily.POS2PMS, "postcharge", Map.of(), PmsPriority.CAFE));
    }

    // (e) Review Focus 3
    @Test
    void replyLargerThanCapThrowsAndCapIsPerFamily() {
        replyWith("x".repeat(3 * MB));
        assertThrows(PmsUpstreamException.class,
                () -> gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
        assertEquals(2 * MB, transport.last().maxBytes());

        assertEquals(3 * MB, gateway.text(EzeeFamily.GUEST_DB, "GuestDatabase", Map.of(), PmsPriority.HUB).length());
        assertEquals(5 * MB, transport.last().maxBytes());
        gateway.text(EzeeFamily.CONNECTIVITY, "HistoricalBookings", Map.of(), PmsPriority.HUB);
        assertEquals(5 * MB, transport.last().maxBytes());
    }

    @Test
    void jdkTransportReadsAtMostCapPlusOneBytes() throws Exception {
        byte[] big = new byte[3 * MB];
        ByteArrayInputStream in = new ByteArrayInputStream(big);
        assertThrows(PmsUpstreamException.class, () -> JdkPmsTransport.readCapped(in, 2 * MB));
        assertEquals(3 * MB - (2 * MB + 1), in.available());

        String small = "héllo";
        assertEquals(small, JdkPmsTransport.readCapped(
                new ByteArrayInputStream(small.getBytes(StandardCharsets.UTF_8)), 2 * MB));
    }

    // (f) per-family request shape
    @Test
    void reservationGetUsesQueryString() {
        gateway.json(EzeeFamily.RESERVATION_GET, "Reservation", Map.of("ResNo", "12 34"), PmsPriority.HUB);
        FakeTransport.Call c = transport.last();
        assertEquals("GET", c.method());
        assertTrue(c.url().startsWith(BASE + "/booking/reservation_api/listing.php?"));
        assertTrue(c.url().contains("request_type=Reservation"));
        assertTrue(c.url().contains("HotelCode=" + HOTEL));
        assertTrue(c.url().contains("APIKey=" + KEY));
        assertTrue(c.url().contains("ResNo=12+34"));
        assertNull(c.body());
    }

    @Test
    void connectivityAndKioskNestCredentialsInAuthentication() throws Exception {
        gateway.json(EzeeFamily.KIOSK, "RetrieveListofBills",
                Map.of("Authentication", Map.of("BookingId", "B9"), "Extra", "v"), PmsPriority.HUB);
        FakeTransport.Call c = transport.last();
        assertEquals("POST", c.method());
        assertEquals(BASE + "/index.php/page/service.kioskconnectivity", c.url());
        JsonNode req = mapper.readTree(c.body()).path("RES_Request");
        assertEquals("RetrieveListofBills", req.path("Request_Type").asText());
        assertEquals(HOTEL, req.path("Authentication").path("HotelCode").asText());
        assertEquals(KEY, req.path("Authentication").path("AuthCode").asText());
        assertEquals("B9", req.path("Authentication").path("BookingId").asText());
        assertEquals("v", req.path("Extra").asText());

        gateway.json(EzeeFamily.CONNECTIVITY, "Bookings", Map.of(), PmsPriority.HUB);
        assertEquals(BASE + "/pmsinterface/pms_connectivity.php", transport.last().url());
    }

    @Test
    void vacationRentalSendsAuthCodeHeaderAndNoKeyInBody() throws Exception {
        replyWith("{\"status\":\"success\",\"data\":{}}");
        gateway.json(EzeeFamily.VACATION_RENTAL, "get_listing", Map.of("from", "2026-11-10"), PmsPriority.HUB);
        FakeTransport.Call c = transport.last();
        assertEquals(KEY, c.headers().get("AUTH_CODE"));
        assertFalse(c.body().contains(KEY));
        JsonNode body = mapper.readTree(c.body());
        assertEquals("get_listing", body.path("request_type").asText());
        assertEquals(HOTEL, body.path("body").path("hotel_id").asText());
        assertEquals("2026-11-10", body.path("body").path("from").asText());
    }

    @Test
    void pos2pmsSendsXmlWithAuth() {
        replyWith("<response><status>ok</status></response>");
        gateway.text(EzeeFamily.POS2PMS, "postcharge", Map.of("amount", "1<2"), PmsPriority.CAFE);
        FakeTransport.Call c = transport.last();
        assertEquals(BASE + "/index.php/page/service.pos2pms", c.url());
        assertEquals("text/xml", c.headers().get("Content-Type"));
        assertTrue(c.body().startsWith("<?xml version=\"1.0\" standalone=\"yes\"?><request><auth>" + KEY + "</auth><oprn>postcharge</oprn>"));
        assertTrue(c.body().contains("<amount>1&lt;2</amount>"));
        assertFalse(c.body().contains(HOTEL));
    }

    @Test
    void bodyCredentialFamilies() throws Exception {
        replyWith(CSV);
        gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of("fromdate", "2026-11-01"), PmsPriority.HUB);
        JsonNode fin = mapper.readTree(transport.last().body());
        assertEquals(KEY, fin.path("authcode").asText());
        assertEquals(HOTEL, fin.path("hotel_code").asText());
        assertEquals("2026-11-01", fin.path("fromdate").asText());

        gateway.text(EzeeFamily.GUEST_DB, "GuestDatabase", Map.of(), PmsPriority.HUB);
        JsonNode guest = mapper.readTree(transport.last().body());
        assertEquals(KEY, guest.path("authkey").asText());
        assertEquals(HOTEL, guest.path("hotel_code").asText());
        assertEquals(BASE + "/index.php/page/service.guestdatabase", transport.last().url());
    }

    // (g) routing
    @Test
    void multiEndpointFamiliesRouteByRequestType() {
        replyWith(CSV);
        gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB);
        assertEquals(BASE + "/index.php/page/service.posting", transport.last().url());
        gateway.text(EzeeFamily.FINANCE, "XERO_GET_RECEIPT_DATA", Map.of(), PmsPriority.HUB);
        assertEquals(BASE + "/index.php/page/service.PMSAccountAPI", transport.last().url());
        gateway.text(EzeeFamily.HOUSEKEEPING, "UpdateRoomStatus", Map.of(), PmsPriority.HUB);
        assertEquals(BASE + "/index.php/page/service.hkupdatestatus", transport.last().url());

        assertEquals("/index.php/page/service.voucher", EzeeFamily.FINANCE.pathFor("HotelExpenses"));
        assertEquals("/index.php/page/service.hkinfoforkaterina", EzeeFamily.HOUSEKEEPING.pathFor("InhouseRoomStatus"));
        assertEquals("/pmsinterface/getdataAPI.php", EzeeFamily.CONNECTIVITY.pathFor("Inventory"));
        assertEquals("/pmsinterface/getdataAPI.php", EzeeFamily.CONNECTIVITY.pathFor("Rate"));
        assertEquals("/pmsinterface/pms_connectivity.php", EzeeFamily.CONNECTIVITY.pathFor("UpdateAvailability"));
        assertThrows(IllegalArgumentException.class, () -> EzeeFamily.HOUSEKEEPING.pathFor("Nope"));
    }

    @Test
    void limiterBusyPropagatesAndNoIoHappens() {
        PmsProperties p = new PmsProperties();
        p.setHubPer5s(0);
        EzeeGateway starved = new EzeeGateway(new PmsRateLimiter(p), transport, BASE, KEY, HOTEL);
        assertThrows(PmsBusyException.class,
                () -> starved.json(EzeeFamily.KIOSK, "FetchBookingRecord", Map.of(), PmsPriority.HUB));
        assertThrows(PmsBusyException.class,
                () -> starved.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void jsonArrayIsSuccessAndBareScalarIsNotJson() {
        replyWith("[]");
        assertTrue(gateway.json(EzeeFamily.HOUSEKEEPING, "InhouseRoomStatus", Map.of(), PmsPriority.HUB).isArray());
        replyWith("101,MARKER-GUEST-Asha,450.00");
        assertThrows(PmsUpstreamException.class,
                () -> gateway.json(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
    }

    // (h) credentials and bodies never reach a log line
    @Test
    void credentialAndBodyNeverLogged() {
        Logger pmsLogger = (Logger) LoggerFactory.getLogger("com.hostel.ordering.pms");
        Level before = pmsLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        pmsLogger.addAppender(appender);
        pmsLogger.setLevel(Level.TRACE);
        try {
            replyWith(CSV);
            gateway.text(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB);
            assertThrows(PmsUpstreamException.class,
                    () -> gateway.json(EzeeFamily.FINANCE, "RetrieveBills", Map.of(), PmsPriority.HUB));
            replyWith("{\"Errors\":{\"ErrorCode\":\"611\",\"ErrorMessage\":\"MARKER-GUEST-Asha\"}}");
            assertThrows(PmsAuthException.class,
                    () -> gateway.json(EzeeFamily.RESERVATION_GET, "Reservation", Map.of(), PmsPriority.HUB));
            transport.failure = new IllegalStateException("MARKER-GUEST-Asha " + KEY);
            assertThrows(PmsUpstreamException.class,
                    () -> gateway.json(EzeeFamily.VACATION_RENTAL, "get_listing", Map.of(), PmsPriority.HUB));
        } finally {
            pmsLogger.detachAppender(appender);
            pmsLogger.setLevel(before);
        }
        assertFalse(appender.list.isEmpty(), "gateway should log family/type/status/duration");
        for (ILoggingEvent ev : appender.list) {
            String line = ev.getFormattedMessage()
                    + (ev.getThrowableProxy() == null ? "" : ev.getThrowableProxy().getMessage());
            assertFalse(line.contains(KEY), line);
            assertFalse(line.contains("MARKER"), line);
        }
    }
}
