package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsFrontDeskControllerTest.Cfg.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PmsFrontDeskControllerTest {

    // not beans: Spring would try to autowire the mock's inherited @Autowired fields
    static final EzeeGateway GATEWAY = mock(EzeeGateway.class);
    static final PmsProperties PROPS = new PmsProperties();
    // 23:50 IST on 2026-11-10, still 10 Nov in UTC
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T18:20:00Z"), ZoneOffset.UTC);

    static PmsFrontDeskController newController() {
        PmsCache cache = new PmsCache();
        return new PmsFrontDeskController(new PmsFrontDeskService(GATEWAY, cache, PROPS,
                new PmsHousekeepingFeed(GATEWAY, cache, PROPS)), new PmsBookingService(GATEWAY, cache, PROPS),
                new PmsGuestService(GATEWAY, cache, PROPS), CLOCK);
    }

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsFrontDeskController controller() {
            return newController();
        }
    }

    @Autowired
    PmsFrontDeskController controller;

    @BeforeEach
    void setUp() throws Exception {
        reset(GATEWAY);
        PROPS.setTtl(Map.of());
        when(GATEWAY.json(eq(EzeeFamily.CONNECTIVITY), any(), any(), any())).thenReturn(PmsFrontDeskServiceTest.json("""
                {"Reservations":{"Reservation":[{"FirstName":"Asha","LastName":"Rao","Phone":"5550001",
                 "Mobile":"5550002","Email":"asha@guest.test","Address":"Secret Lane","Source":"Direct",
                 "BookingTran":[{"SubBookingId":"9001","RoomName":"D1","RoomTypeName":"Dorm","Start":"2026-11-10",
                 "End":"2026-11-12","CurrentStatus":"Confirmed","Phone":"5550003","Email":"t@guest.test"}]}]}}"""));
        when(GATEWAY.json(any(), eq("InhouseRoomStatus"), any(), any())).thenReturn(
                PmsFrontDeskServiceTest.json("{\"checkinguestlist\":[{\"reservationno\":\"7\",\"guestname\":\"R K\","
                        + "\"email\":\"r@guest.test\",\"address\":\"Secret Lane\"}]}"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
    }

    @Test
    void staffAdminAndSuperAdminAreServed() {
        for (String role : new String[] {"ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            as(role);
            PmsFrontDeskController.Listing l = controller.arrivals(null, null);
            assertEquals(1, l.items().size());
            assertFalse(l.stale());
            assertEquals(1, controller.inhouse().items().size());
            controller.departures(null, null);
        }
    }

    @Test
    void unauthenticatedAndOtherRolesAreRefused() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.arrivals(null, null));
        as("ROLE_GUEST");
        assertThrows(AccessDeniedException.class, controller::inhouse);
        verifyNoInteractions(GATEWAY);
    }

    @Test
    void defaultRangeIsTodayInIst() {
        as("ROLE_STAFF");
        controller.arrivals(null, null);
        verify(GATEWAY).json(eq(EzeeFamily.CONNECTIVITY), eq("ArrivalList"),
                eq(Map.of("Date", Map.of("from_date", "2026-11-10", "to_date", "2026-11-10"))),
                eq(PmsPriority.HUB));
    }

    @Test
    void badRangesAreHttp400AndNeverReachEzee() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(newController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String q : new String[] {"from=2026-11-12&to=2026-11-10", "from=2026-11-01&to=2026-12-05",
                "from=11/10/2026", "to=garbage"}) {
            for (String path : new String[] {"/pms/arrivals?", "/pms/departures?"}) {
                mvc.perform(get(path + q)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isBadRequest());
            }
        }
        verifyNoInteractions(GATEWAY);
    }

    @Test
    void listBodiesCarryNoContactData() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(newController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String path : new String[] {"/pms/arrivals", "/pms/inhouse"}) {
            String body = mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
            assertTrue(body.contains("\"items\"") && body.contains("\"asOf\"") && body.contains("\"stale\":false"));
            String lower = body.toLowerCase();
            for (String banned : new String[] {"phone", "email", "mobile", "address", "5550", "guest.test",
                    "secret lane"}) {
                assertFalse(lower.contains(banned), path + " leaked " + banned);
            }
        }
        new ObjectMapper().readTree(mvc.perform(get("/pms/arrivals")).andReturn().getResponse().getContentAsString());
    }

    @Test
    void bookingDetailRolesAndBadIds() throws Exception {
        when(GATEWAY.json(eq(EzeeFamily.CONNECTIVITY), eq("FetchSingleBooking"), any(), any())).thenReturn(
                PmsFrontDeskServiceTest.json(PmsBookingServiceTest.BOOKING));
        when(GATEWAY.json(eq(EzeeFamily.KIOSK), any(), any(), any())).thenReturn(
                PmsFrontDeskServiceTest.json(PmsBookingServiceTest.BILLS));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.booking("9001-1"));
        as("ROLE_GUEST");
        assertThrows(AccessDeniedException.class, () -> controller.booking("9001-1"));
        for (String role : new String[] {"ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            as(role);
            PmsFrontDeskController.Detail d = controller.booking("9001-1");
            assertEquals(2, d.item().folios().size());
            assertFalse(d.stale());
        }
        MockMvc mvc = MockMvcBuilders.standaloneSetup(newController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String bad : new String[] {"abc", "1-2345", "1111111111111111111111", "%20"}) {
            mvc.perform(get("/pms/bookings/" + bad)).andExpect(
                    org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        mvc.perform(get("/pms/bookings/9001-1")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        when(GATEWAY.json(eq(EzeeFamily.CONNECTIVITY), eq("FetchSingleBooking"), any(), any()))
                .thenReturn(com.fasterxml.jackson.databind.node.NullNode.getInstance());
        mvc.perform(get("/pms/bookings/5")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void guestSearchRolesBodyAndBadQueries() throws Exception {
        when(GATEWAY.json(eq(EzeeFamily.CONNECTIVITY), eq("GuestList"), any(), any())).thenReturn(
                PmsFrontDeskServiceTest.json(PmsGuestServiceTest.guests("Asha Rao:1:Yes")));
        when(GATEWAY.text(eq(EzeeFamily.GUEST_DB), any(), any(), any())).thenReturn("Name\nAsha Rao\n");
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.guests("asha"));
        as("ROLE_GUEST");
        assertThrows(AccessDeniedException.class, () -> controller.guests("asha"));
        for (String role : new String[] {"ROLE_STAFF", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            as(role);
            assertEquals(1, controller.guests("asha").items().size());
        }
        MockMvc mvc = MockMvcBuilders.standaloneSetup(newController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = mvc.perform(get("/pms/guests?q=asha")).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"stays\":1") && body.contains("\"vip\":true") && body.contains("\"asOf\""));
        for (String banned : new String[] {"5550009", "secret lane", "mobile", "phone", "address"}) {
            assertFalse(body.toLowerCase().contains(banned), "leaked " + banned);
        }
        for (String bad : new String[] {"", "?q=", "?q=a", "?q=" + "x".repeat(61)}) {
            mvc.perform(get("/pms/guests" + bad)).andExpect(
                    org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
    }
}
