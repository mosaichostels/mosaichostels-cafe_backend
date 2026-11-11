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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.hostel.ordering.pms.dto.GuestHit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PmsGuestServiceTest {

    EzeeGateway gateway;
    PmsProperties props;
    PmsGuestService service;

    @BeforeEach
    void setUp() {
        gateway = mock(EzeeGateway.class);
        props = new PmsProperties();
        service = new PmsGuestService(gateway, new PmsCache(), props);
        stubGuests(PmsFrontDeskServiceTest.json(guests("Asha Rao:1:Yes", "asha rao:2:No", "Bram Quill:3:",
                "Chen (Wu):4:No", "A.B*[x:5:No", "Zed Asha:6:1")));
        when(gateway.text(eq(EzeeFamily.GUEST_DB), any(), any(), any())).thenReturn(
                "\"Guest Name\",\"Guest Email\",\"Total Number of stays\"\n\"Rao, Asha\",x,5\nAsha Rao,x,1\n\" ASHA RAO \",x,1\n\"Bram \"\"Q\"\"\",x,7\n");
    }

    private void stubGuests(JsonNode n) {
        when(gateway.json(eq(EzeeFamily.CONNECTIVITY), eq("GuestList"), eq(Map.of()), eq(PmsPriority.HUB)))
                .thenReturn(n);
    }

    /** name:id:vip entries, with phone/email/address present to prove they are dropped. */
    static String guests(String... entries) {
        StringBuilder sb = new StringBuilder("{\"Guests\":[");
        for (int i = 0; i < entries.length; i++) {
            String[] p = entries[i].split(":", -1);
            sb.append(i > 0 ? "," : "").append("{\"Id\":\"").append(p[1]).append("\",\"Contact_person\":\"")
                    .append(p[0]).append("\",\"VIPStatus\":\"").append(p[2])
                    .append("\",\"Mobile\":\"5550009\",\"Phone\":null,\"Address\":\"Secret Lane\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void thousandsOfRowsReturnAtMostTwentySorted() {
        String[] many = new String[1600];
        for (int i = 0; i < many.length; i++) {
            many[i] = "Guest " + String.format("%04d", 1600 - i) + ":" + i + ":No";
        }
        stubGuests(PmsFrontDeskServiceTest.json(guests(many)));
        List<GuestHit> hits = service.search("guest").value();
        assertEquals(20, hits.size());
        assertEquals("Guest 0001", hits.get(0).name());
        assertEquals("Guest 0020", hits.get(19).name());
    }

    @Test
    void shortBlankAndLongQueriesAreRejectedBeforeAnyCall() {
        for (String q : new String[] {null, "", " ", "a", " a ", "x".repeat(61)}) {
            assertThrows(IllegalArgumentException.class, () -> service.search(q));
        }
        verify(gateway, times(0)).json(any(), any(), any(), any());
        assertEquals(60, "x".repeat(60).length());
        service.search("x".repeat(60));
    }

    @Test
    void regexMetacharactersAreLiteral() {
        assertEquals(0, service.search(".*").value().size());
        assertEquals(1, service.search("n (").value().size());
        assertEquals(0, service.search("n .").value().size());
        assertEquals("A.B*[x", service.search("[x").value().get(0).name());
        assertEquals(1, service.search("b*[").value().size());
    }

    @Test
    void matchIsCaseInsensitiveSortedByNameThenId() {
        List<GuestHit> hits = service.search("  ASHA ").value();
        assertEquals(List.of("1", "2", "6"), hits.stream().map(GuestHit::id).toList());
        assertEquals(List.of("Asha Rao", "asha rao", "Zed Asha"), hits.stream().map(GuestHit::name).toList());
        assertTrue(hits.get(0).vip());
        assertFalse(hits.get(1).vip());
        assertTrue(hits.get(2).vip());
        assertFalse(service.search("bram").value().get(0).vip());
    }

    @Test
    void emptyGuestListGivesEmptyItems() {
        stubGuests(NullNode.getInstance());
        Cached<List<GuestHit>> c = service.search("asha");
        assertTrue(c.value().isEmpty());
        assertFalse(c.stale());
    }

    @Test
    void staysComeFromCsvByExactNameAndAreCachedOnce() {
        List<GuestHit> hits = service.search("asha rao").value();
        assertEquals(2, hits.get(0).stays());
        assertEquals(2, hits.get(1).stays());
        assertEquals(0, service.search("chen").value().get(0).stays());
        service.search("bram");
        verify(gateway, times(1)).json(any(), eq("GuestList"), any(), any());
        verify(gateway, times(1)).text(any(), any(), any(), any());
    }

    @Test
    void badCsvHeaderGivesZeroStays() {
        when(gateway.text(eq(EzeeFamily.GUEST_DB), any(), any(), any())).thenReturn("a,b\nAsha Rao,2\n");
        assertEquals(0, service.search("asha rao").value().get(0).stays());
    }

    @Test
    void csvFailureDoesNotFailSearchAndIsNotCached() {
        when(gateway.text(eq(EzeeFamily.GUEST_DB), any(), any(), any())).thenThrow(new PmsUpstreamException("down"))
                .thenReturn("Guest Name,Total Number of stays\nAsha Rao,3\n");
        assertEquals(0, service.search("asha rao").value().get(0).stays());
        assertEquals(3, service.search("asha rao").value().get(0).stays());
    }

    @Test
    void staleListPassesThrough() {
        props.setTtl(Map.of("guests", 0L));
        Cached<List<GuestHit>> good = service.search("asha");
        when(gateway.json(any(), eq("GuestList"), any(), any())).thenThrow(new PmsUpstreamException("down"));
        Cached<List<GuestHit>> again = service.search("asha");
        assertTrue(again.stale());
        assertEquals(good.fetchedAtMs(), again.fetchedAtMs());
        assertEquals(good.value(), again.value());
    }

    @Test
    void listFailureWithNoPriorValuePropagates() {
        when(gateway.json(any(), eq("GuestList"), any(), any())).thenThrow(new PmsUpstreamException("down"));
        assertThrows(PmsUpstreamException.class, () -> service.search("asha"));
    }

    @Test
    void parserHandlesQuotedFieldsAndLineBreaks() {
        Map<String, Integer> m = PmsGuestService.countStays("﻿\"Guest Name\",\"Total Number of stays\"\r\n\"Li\nne, \"\"X\"\"\",1\r\nb,2\r\nB,x\r\nB,3\r\n");
        assertEquals(Map.of("li\nne, \"x\"", 1, "b", 5), m);
        assertTrue(PmsGuestService.countStays("").isEmpty());
        assertTrue(PmsGuestService.countStays("\n\n").isEmpty());
    }

    @Test
    void nothingPersonalReachesTheLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level old = root.getLevel();
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        root.addAppender(app);
        root.setLevel(Level.ALL);
        try {
            service.search("asha rao");
            service.search("Secret");
            when(gateway.text(eq(EzeeFamily.GUEST_DB), any(), any(), any())).thenThrow(new PmsUpstreamException("x"));
            service.search("bram quill");
        } finally {
            root.detachAppender(app);
            root.setLevel(old);
        }
        for (ILoggingEvent e : app.list) {
            String m = (e.getFormattedMessage() + " " + e.getThrowableProxy()).toLowerCase();
            for (String banned : new String[] {"asha", "rao", "bram", "quill", "secret", "5550009", "chen"}) {
                assertFalse(m.contains(banned), "log leaked " + banned + ": " + m);
            }
        }
    }
}
