package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.hostel.ordering.pms.dto.BookingSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PmsFrontDeskServiceTest {

    static final ObjectMapper OM = new ObjectMapper();
    static final LocalDate D = LocalDate.of(2026, 11, 10);

    EzeeGateway gateway;
    PmsProperties props;
    PmsFrontDeskService service;

    @BeforeEach
    void setUp() {
        gateway = mock(EzeeGateway.class);
        props = new PmsProperties();
        PmsCache cache = new PmsCache();
        service = new PmsFrontDeskService(gateway, cache, props, new PmsHousekeepingFeed(gateway, cache, props));
    }

    static JsonNode json(String s) {
        try {
            return OM.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final String RESERVATIONS = """
            {"Reservations":{"Reservation":[
              {"UniqueID":"900","FirstName":"Asha","LastName":"Rao","Phone":"111","Mobile":"222","Email":"a@x.test",
               "Address":"1 Test Road","Source":"Walk-in",
               "BookingTran":[{"SubBookingId":"9001-1","RoomName":"D1","RoomTypeName":"Dorm","Start":"2026-11-10",
                 "End":"2026-11-13","CurrentStatus":"Confirmed","Phone":"333","Email":"t@x.test"},
                {"SubBookingId":"9001-2","RoomName":"D2","RoomTypeName":"Dorm","Start":"2026-11-10",
                 "End":"2026-11-11","CurrentStatus":"Confirmed"}]},
              {"UniqueID":"901","FirstName":"","LastName":"Solo","Source":"Booking.com",
               "BookingTran":[{"SubBookingId":"9002","RoomName":"P1","RoomTypeName":"Private","Start":"2026-11-12",
                 "End":"2026-11-10","CurrentStatus":"Checked-in"}]},
              {"UniqueID":"902","BookingTran":[{"SubBookingId":"9003","Start":"bad","End":"2026-11-10"}]}
            ]}}
            """;

    private void stubConnectivity(String type, JsonNode reply) {
        when(gateway.json(eq(EzeeFamily.CONNECTIVITY), eq(type),
                eq(Map.of("Date", Map.of("from_date", "2026-11-10", "to_date", "2026-11-10"))),
                eq(PmsPriority.HUB))).thenReturn(reply);
    }

    @Test
    void arrivalsMapsOneRowPerBookingTran() {
        stubConnectivity("ArrivalList", json(RESERVATIONS));
        List<BookingSummary> items = service.arrivals(D, D).value();
        assertEquals(4, items.size());
        assertEquals(new BookingSummary("9001-1", "Asha Rao", "D1", "Dorm", "2026-11-10", "2026-11-13", 3,
                "Confirmed", "Walk-in"), items.get(0));
        assertEquals(new BookingSummary("9001-2", "Asha Rao", "D2", "Dorm", "2026-11-10", "2026-11-11", 1,
                "Confirmed", "Walk-in"), items.get(1));
        // missing first name trims cleanly; negative span never goes below 0
        assertEquals("Solo", items.get(2).guestName());
        assertEquals(0, items.get(2).nights());
        // missing fields become empty strings, unparsable dates give 0 nights
        assertEquals(new BookingSummary("9003", "", "", "", "bad", "2026-11-10", 0, "", ""), items.get(3));
    }

    @Test
    void departuresUsesDepartureList() {
        stubConnectivity("DepartureList", json(RESERVATIONS));
        assertEquals(4, service.departures(D, D).value().size());
    }

    @Test
    void emptyAndNullRepliesGiveEmptyList() {
        stubConnectivity("ArrivalList", NullNode.instance);
        assertTrue(service.arrivals(D, D).value().isEmpty());
        stubConnectivity("DepartureList", json("{\"Reservations\":{\"Reservation\":[]}}"));
        assertTrue(service.departures(D, D).value().isEmpty());
    }

    @Test
    void cacheKeyHoldsNoGuestData() {
        // the key is built from the name and dates only; verified through a recording cache
        List<String> keys = new java.util.ArrayList<>();
        PmsCache recording = new PmsCache() {
            @Override
            public <T> Cached<T> getOrLoad(String key, java.time.Duration ttl, java.util.concurrent.Callable<T> l)
                    throws Exception {
                keys.add(key);
                return super.getOrLoad(key, ttl, l);
            }
        };
        stubConnectivity("ArrivalList", json(RESERVATIONS));
        new PmsFrontDeskService(gateway, recording, props, new PmsHousekeepingFeed(gateway, recording, props))
                .arrivals(D, D);
        assertEquals(List.of("arrivals:2026-11-10:2026-11-10"), keys);
        assertFalse(keys.get(0).contains("Asha"));
    }

    @Test
    void failureAfterGoodLoadServesStaleWithOldAsOf() {
        props.setTtl(Map.of("arrivals", 0L));
        stubConnectivity("ArrivalList", json(RESERVATIONS));
        Cached<List<BookingSummary>> good = service.arrivals(D, D);
        when(gateway.json(any(), any(), any(), any())).thenThrow(new PmsUpstreamException("down"));
        Cached<List<BookingSummary>> again = service.arrivals(D, D);
        assertTrue(again.stale());
        assertEquals(good.fetchedAtMs(), again.fetchedAtMs());
        assertEquals(good.value(), again.value());
    }

    @Test
    void failureWithNoPriorValuePropagates() {
        when(gateway.json(any(), any(), any(), any())).thenThrow(new PmsUpstreamException("down"));
        assertThrows(PmsUpstreamException.class, () -> service.arrivals(D, D));
        assertThrows(PmsUpstreamException.class, () -> service.inHouse());
    }

    @Test
    void inHouseMapsCheckinguestlistLeniently() {
        when(gateway.json(eq(EzeeFamily.HOUSEKEEPING), eq("InhouseRoomStatus"), eq(Map.of()), eq(PmsPriority.HUB)))
                .thenReturn(json("""
                        {"roomlist":[{"roomid":"1"}],"checkinguestlist":[
                         {"reservationno":"77","guestname":"Ravi K","room":"D3","roomtype":"Dorm",
                          "checkindate":"2026-11-08 14:00:00","checkoutdate":"2026-11-11","bookingstatus":"Checked-in",
                          "businesssource":"Direct","guestmobile":"999","guestemail":"r@x.test"},
                         {"reservationno":"78","checkindate":"08/11/2026","checkoutdate":null}
                        ]}"""));
        List<BookingSummary> items = service.inHouse().value();
        assertEquals(new BookingSummary("77", "Ravi K", "D3", "Dorm", "2026-11-08", "2026-11-11", 3, "Checked-in",
                "Direct"), items.get(0));
        assertEquals(new BookingSummary("78", "", "", "", "", "", 0, "", ""), items.get(1));
    }

    @Test
    void inHouseDropsCheckedOutAndNotYetArrived() {
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(json("""
                {"checkinguestlist":[
                 {"reservationno":"1","bookingstatus":"Checked Out"},
                 {"reservationno":"2","bookingstatus":"Confirmed Reservation"},
                 {"reservationno":"3","bookingstatus":"Stayover"},
                 {"reservationno":"4","bookingstatus":"Due Out"},
                 {"reservationno":"5","bookingstatus":"Arrived"}]}"""));
        assertEquals(List.of("3", "4", "5"),
                service.inHouse().value().stream().map(BookingSummary::bookingId).toList());
    }

    @Test
    void inHouseWithNoGuestsIsEmpty() {
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(NullNode.instance);
        assertTrue(service.inHouse().value().isEmpty());
    }

    @Test
    void feedServesCachedCopyWithinTtl() {
        PmsCache cache = new PmsCache();
        PmsHousekeepingFeed feed = new PmsHousekeepingFeed(gateway, cache, props);
        when(gateway.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(json("{\"roomlist\":[]}"));
        Cached<JsonNode> a = feed.inhouseStatus();
        Cached<JsonNode> b = feed.inhouseStatus();
        assertEquals(a.fetchedAtMs(), b.fetchedAtMs());
        verify(gateway, times(1)).json(eq(EzeeFamily.HOUSEKEEPING), eq("InhouseRoomStatus"), eq(Map.of()),
                eq(PmsPriority.HUB));
    }

    @Test
    void helpersParseAndValidate() {
        Clock late = Clock.fixed(Instant.parse("2026-11-10T18:20:00Z"), ZoneOffset.UTC); // 23:50 IST
        Clock early = Clock.fixed(Instant.parse("2026-11-10T18:40:00Z"), ZoneOffset.UTC); // 00:10 IST next day
        assertEquals(LocalDate.of(2026, 11, 10), PmsDates.today(late));
        assertEquals(LocalDate.of(2026, 11, 11), PmsDates.today(early));
        assertEquals(new PmsDates.Range(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 11)),
                PmsDates.range(null, "", early));
        assertEquals(30, PmsDates.range("2026-11-01", "2026-12-01", late).to().toEpochDay()
                - PmsDates.range("2026-11-01", "2026-12-01", late).from().toEpochDay());
        assertThrows(IllegalArgumentException.class, () -> PmsDates.range("2026-11-02", "2026-11-01", late));
        assertThrows(IllegalArgumentException.class, () -> PmsDates.range("2026-11-01", "2026-12-02", late));
        assertThrows(IllegalArgumentException.class, () -> PmsDates.range("nope", null, late));
        assertEquals("123-45", PmsIds.bookingId("123-45"));
        for (String bad : new String[] {"", "abc", "1-2-3", "1-1234", "123456789012345678901", "1;x", null}) {
            assertThrows(IllegalArgumentException.class, () -> PmsIds.bookingId(bad));
        }
    }
}
