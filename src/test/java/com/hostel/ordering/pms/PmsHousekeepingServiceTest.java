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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.NullNode;
import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.pms.dto.RoomStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsHousekeepingServiceTest.Cfg.class)
class PmsHousekeepingServiceTest {

    // not beans: Spring would try to autowire the mock's inherited @Autowired fields
    static final EzeeGateway GATEWAY = mock(EzeeGateway.class);

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsHousekeepingController controller() {
            return new PmsHousekeepingController(
                    new PmsHousekeepingService(new PmsHousekeepingFeed(GATEWAY, new PmsCache(), new PmsProperties())));
        }
    }

    @Autowired
    PmsHousekeepingController controller;

    AtomicLong clock = new AtomicLong(1_000);
    PmsHousekeepingFeed feed;
    PmsHousekeepingService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(GATEWAY);
        PmsCache cache = new PmsCache(clock::get);
        feed = new PmsHousekeepingFeed(GATEWAY, cache, new PmsProperties());
        service = new PmsHousekeepingService(feed);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void reply(String json) {
        when(GATEWAY.json(eq(EzeeFamily.HOUSEKEEPING), eq("InhouseRoomStatus"), any(), eq(PmsPriority.HUB)))
                .thenReturn(PmsTestJson.json(json));
    }

    private static String room(String id, String name, String hk, String blocked) {
        return "{\"hotel_code\":\"1\",\"roomid\":\"" + id + "\",\"unitid\":\"u" + id + "\",\"roomname\":\"" + name
                + "\",\"roomtypeid\":\"t" + id + "\",\"roomtypename\":\"Dorm\",\"isblocked\":\"" + blocked
                + "\",\"hkstatus\":\"" + hk + "\",\"hkremarks\":\"\",\"roomstatus\":\"Vacant\"}";
    }

    private static String guest(String room, String name) {
        return "{\"reservationno\":\"1\",\"guestname\":\"" + name + "\",\"email\":\"q@guest.test\","
                + "\"address\":\"Secret Lane\",\"room\":\"" + room + "\"}";
    }

    @Test
    void fortySevenRoomsInFortySevenOut() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 47; i++) {
            sb.append(i > 1 ? "," : "").append(room("" + (100 + i), "" + i, "Dirty", "No"));
        }
        reply("{\"roomlist\":[" + sb + "],\"checkinguestlist\":[]}");
        Cached<List<RoomStatus>> c = service.rooms();
        assertEquals(47, c.value().size());
        assertEquals(new RoomStatus("101", "u101", "t101", "1", "Dorm", "Dirty", false, false, ""), c.value().get(0));
    }

    @Test
    void occupiedComesFromGuestListMatchedByTrimmedCaseInsensitiveRoom() {
        reply("{\"roomlist\":[" + room("1", "A1", "Clean", "No") + "," + room("2", "b2", "", "No") + ","
                + room("3", "C3", "Clean", "No") + "],\"checkinguestlist\":[" + guest(" a1 ", "Asha Rao") + ","
                + guest("B2", "Ben") + "]}");
        List<RoomStatus> r = service.rooms().value();
        assertTrue(r.get(0).occupied());
        assertEquals("Asha Rao", r.get(0).guestName());
        assertTrue(r.get(1).occupied());
        assertEquals("Ben", r.get(1).guestName());
        assertFalse(r.get(2).occupied());
        assertEquals("", r.get(2).guestName());
    }

    @Test
    void dormWithSeveralGuestsIsOccupiedWithFirstGuestName() {
        reply("{\"roomlist\":[" + room("1", "D1", "Dirty", "No") + "],\"checkinguestlist\":[" + guest("D1", "First One")
                + "," + guest("D1", "Second Two") + "]}");
        RoomStatus r = service.rooms().value().get(0);
        assertTrue(r.occupied());
        assertEquals("First One", r.guestName());
    }

    @Test
    void blockedMapsFromIsblocked() {
        reply("{\"roomlist\":[" + room("1", "1", "", "Yes") + "," + room("2", "2", "", " yes ") + ","
                + room("3", "3", "", "No") + "," + room("4", "4", "", "") + "]}");
        List<RoomStatus> r = service.rooms().value();
        assertEquals(List.of(true, true, false, false), r.stream().map(RoomStatus::blocked).toList());
    }

    @Test
    void hkStatusPassesThroughUnchanged() {
        reply("{\"roomlist\":[" + room("1", "1", "", "No") + "," + room("2", "2", "Dirty", "No") + ","
                + room("3", "3", "Clean", "No") + "," + room("4", "4", "Inspected ?!", "No") + "]}");
        assertEquals(List.of("", "Dirty", "Clean", "Inspected ?!"),
                service.rooms().value().stream().map(RoomStatus::hkStatus).toList());
    }

    @Test
    void sortsByNaturalNameThenRoomId() {
        reply("{\"roomlist\":[" + room("9", "10", "", "No") + "," + room("8", "2", "", "No") + ","
                + room("7", "D1", "", "No") + "," + room("6", "1", "", "No") + "," + room("5", "D1", "", "No") + ","
                + room("4", "d02", "", "No") + "]}");
        assertEquals(List.of("1", "2", "10", "D1", "D1", "d02"),
                service.rooms().value().stream().map(RoomStatus::name).toList());
        assertEquals(List.of("5", "7"), service.rooms().value().stream().filter(x -> x.name().equals("D1"))
                .map(RoomStatus::roomId).toList());
    }

    @Test
    void emptyAndMalformedRepliesGiveNoRoomsAndBlankIdsAreSkipped() {
        when(GATEWAY.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(NullNode.getInstance());
        assertTrue(service.rooms().value().isEmpty());
        clock.addAndGet(Duration.ofSeconds(31).toMillis());
        reply("{\"checkinguestlist\":[" + guest("1", "X") + "]}");
        assertTrue(service.rooms().value().isEmpty());
        clock.addAndGet(Duration.ofSeconds(31).toMillis());
        reply("{\"roomlist\":[" + room("", "1", "", "No") + "," + room("2", "2", "", "No") + "]}");
        List<RoomStatus> r = service.rooms().value();
        assertEquals(1, r.size());
        assertFalse(r.get(0).occupied());
    }

    @Test
    void staleCachedValueIsServedWhenEzeeIsDown() {
        reply("{\"roomlist\":[" + room("1", "1", "Dirty", "No") + "]}");
        assertFalse(service.rooms().stale());
        clock.addAndGet(Duration.ofSeconds(31).toMillis());
        when(GATEWAY.json(any(), eq("InhouseRoomStatus"), any(), any())).thenThrow(new PmsUpstreamException("x"));
        Cached<List<RoomStatus>> c = service.rooms();
        assertTrue(c.stale());
        assertEquals(1_000, c.fetchedAtMs());
        assertEquals(1, c.value().size());
    }

    @Test
    void roleMatrix() {
        reply("{\"roomlist\":[" + room("1", "1", "", "No") + "]}");
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.rooms());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", "ROLE_GUEST"));
        assertThrows(AccessDeniedException.class, () -> controller.rooms());
        verifyNoInteractions(GATEWAY);
        for (String role : new String[] {"ROLE_HOUSEKEEPING", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
            PmsHousekeepingController.Rooms body = controller.rooms();
            assertEquals(1, body.items().size());
            assertFalse(body.stale());
        }
    }

    @Test
    void guestNamesNeverReachLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level old = root.getLevel();
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        root.setLevel(Level.ALL);
        root.addAppender(app);
        try {
            reply("{\"roomlist\":[" + room("1", "1", "", "No") + "],\"checkinguestlist\":[" + guest("1", "Zed Quill")
                    + "]}");
            service.rooms();
            clock.addAndGet(Duration.ofSeconds(31).toMillis());
            when(GATEWAY.json(any(), eq("InhouseRoomStatus"), any(), any())).thenThrow(new PmsUpstreamException("x"));
            service.rooms();
            assertFalse(app.list.isEmpty(), "stale path should have logged something to inspect");
            for (ILoggingEvent e : app.list) {
                String m = e.getFormattedMessage() + e.getThrowableProxy();
                for (String pii : new String[] {"Zed", "Quill", "guest.test", "Secret"}) {
                    assertFalse(m.contains(pii), "log leaked " + pii + ": " + m);
                }
            }
        } finally {
            root.detachAppender(app);
            root.setLevel(old);
        }
    }

    @Test
    void namelessRoomPicksUpNoGuest() {
        reply("{\"roomlist\":[" + room("1", "", "", "No") + "],\"checkinguestlist\":[" + guest("", "Ghost") + "]}");
        RoomStatus r = service.rooms().value().get(0);
        assertFalse(r.occupied());
        assertEquals("", r.guestName());
    }

    private static final String[] ALPHA = {"a", "A", "b", "B", "0", "1", "2", "\u0663", " ", ""};

    private static void gen(String cur, int depth, List<String> out) {
        out.add(cur);
        if (depth == 0) {
            return;
        }
        for (String c : ALPHA) {
            if (!c.isEmpty()) {
                gen(cur + c, depth - 1, out);
            }
        }
    }

    @Test
    void naturalIsAntisymmetricAndTransitive() {
        List<String> all = new java.util.ArrayList<>();
        gen("", 4, all);
        java.util.Random rnd = new java.util.Random(42);
        for (String a : all) {
            for (int k = 0; k < 40; k++) {
                String b = all.get(rnd.nextInt(all.size()));
                assertEquals(Integer.signum(PmsHousekeepingService.natural(a, b)),
                        -Integer.signum(PmsHousekeepingService.natural(b, a)), a + "|" + b);
            }
        }
        for (int k = 0; k < 400_000; k++) {
            String a = all.get(rnd.nextInt(all.size()));
            String b = all.get(rnd.nextInt(all.size()));
            String c = all.get(rnd.nextInt(all.size()));
            if (PmsHousekeepingService.natural(a, b) <= 0 && PmsHousekeepingService.natural(b, c) <= 0) {
                assertTrue(PmsHousekeepingService.natural(a, c) <= 0, a + "|" + b + "|" + c);
            }
        }
    }

    @Test
    void naturalExplicitCases() {
        int n = 0;
        assertTrue(PmsHousekeepingService.natural("2", "10") < 0);
        assertTrue(PmsHousekeepingService.natural("D1", "D2") < 0);
        assertTrue(PmsHousekeepingService.natural("Room 2", "Room 02") != 0);
        assertEquals(-Integer.signum(PmsHousekeepingService.natural("Room 02", "Room 2")),
                Integer.signum(PmsHousekeepingService.natural("Room 2", "Room 02")));
        String big = "9".repeat(45);
        assertTrue(PmsHousekeepingService.natural("1" + "0".repeat(44), big) < 0);
        assertTrue(PmsHousekeepingService.natural("", "a") < 0);
        assertTrue(PmsHousekeepingService.natural("1", "a") < 0);
        String[][] triples = {{"b1b1", "B1B2", "b01b"}, {"aa001a", "aA1B", "aa001"}};
        for (String[] t : triples) {
            for (String a : t) {
                for (String b : t) {
                    for (String c : t) {
                        if (PmsHousekeepingService.natural(a, b) <= 0 && PmsHousekeepingService.natural(b, c) <= 0) {
                            assertTrue(PmsHousekeepingService.natural(a, c) <= 0, a + b + c);
                        }
                    }
                }
            }
        }
    }

    @Test
    void shuffledBoardAlwaysSorts() {
        List<String> rooms = new java.util.ArrayList<>();
        for (int i = 1; i <= 47; i++) {
            rooms.add(room("" + (100 + i), i % 5 == 0 ? "D" + i : i % 7 == 0 ? "r0" + i : "" + i, "", "No"));
        }
        java.util.Random rnd = new java.util.Random(7);
        for (int k = 0; k < 200; k++) {
            java.util.Collections.shuffle(rooms, rnd);
            clock.addAndGet(Duration.ofSeconds(31).toMillis());
            reply("{\"roomlist\":[" + String.join(",", rooms) + "]}");
            assertEquals(47, service.rooms().value().size());
        }
    }
}
