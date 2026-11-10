package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.pms.dto.ChargeCheck;
import com.hostel.ordering.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
@ContextConfiguration(classes = PmsReconcileServiceTest.Cfg.class)
class PmsReconcileServiceTest {

    // not beans: Spring would try to autowire the mocks' inherited fields
    static final EzeeGateway GATEWAY = mock(EzeeGateway.class);
    static final OrderRepository ORDERS = mock(OrderRepository.class);
    // 2026-11-10 11:30 IST
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC);
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static final String HEADER = "\"Unique id\",\"Hotel Name\",\"Hotel Code\",\"Folio No\",\"Date\","
            + "\"VoucherNo/ReceiptNo\",\"Invoice Number\",\"Guest Name\",\"Bill To Name\",\"Type\",\"Particular\","
            + "\"Qty\",\"Currency\",\"Amount\",\"GST Rate\"\r\n";

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsReconcileController controller() {
            return new PmsReconcileController(
                    new PmsReconcileService(ORDERS, GATEWAY, new PmsCache(), new PmsProperties(), CLOCK));
        }
    }

    @Autowired
    PmsReconcileController controller;

    AtomicLong cacheClock = new AtomicLong(1_000);
    PmsReconcileService service;

    @BeforeEach
    void setUp() {
        reset(GATEWAY, ORDERS);
        service = new PmsReconcileService(ORDERS, GATEWAY, new PmsCache(cacheClock::get), new PmsProperties(), CLOCK);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ---- fixtures (synthetic) ----

    static long ist(LocalDate d, int hour) {
        return LocalDateTime.of(d, java.time.LocalTime.of(hour, 0)).atZone(PmsDates.IST).toInstant().toEpochMilli();
    }

    static OrderItem item(String id, double subtotal) {
        OrderItem i = new OrderItem();
        i.setMenuItemId(id);
        i.setMenuItemName("Dish " + id);
        i.setQuantity(1);
        i.setPrice(subtotal);
        i.setSubtotal(subtotal);
        return i;
    }

    static Order order(String id, String status, String folio, double total) {
        Order o = new Order();
        o.setId(id);
        o.setBookingName("Zed Quill");
        o.setDormitory("Dorm Q");
        o.setStatus("DELIVERED");
        o.setTotalAmount(total);
        o.setCreatedAt(ist(DAY, 9));
        o.setItems(new ArrayList<>(List.of(item("m1", total))));
        o.setChargePostStatus(status);
        if (status != null) {
            o.setChargePostFolio(folio);
            o.setChargePostRoom("R7");
            o.setChargePostAt(ist(DAY, 12));
            o.setChargePostedItems(new ArrayList<>(List.of("m1#0")));
        }
        return o;
    }

    static String row(String folio, String date, String amount) {
        return row(folio, date, amount, "Extra Charges", "Food Charge");
    }

    static String row(String folio, String date, String amount, String type, String particular) {
        return "\"1\",\"Synthetic Hostel\",\"H1\",\"" + folio + "\",\"" + date + "\",\"V1\",\"I1\",\"Zed Quill\","
                + "\"Zed Quill\",\"" + type + "\",\"" + particular + "\",\"1.0000\",\"INR\",\"" + amount
                + "\",\"0\"\r\n";
    }

    private void orders(Order... os) {
        when(ORDERS.findForChargeReconcile(anyLong(), anyLong(), anyInt())).thenReturn(new ArrayList<>(List.of(os)));
    }

    private void csv(String body) {
        when(GATEWAY.text(eq(EzeeFamily.FINANCE), eq("RetrieveBills"), any(), eq(PmsPriority.HUB))).thenReturn(body);
    }

    private List<String> statuses(String date) {
        return service.charges(date).items().stream().map(ChargeCheck::eZeeStatus).toList();
    }

    // ---- roles ----

    @Test
    void roleMatrix() {
        orders(order("o1", null, null, 10));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.charges(null));
        for (String role : new String[] {"ROLE_GUEST", "ROLE_STAFF"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
            assertThrows(AccessDeniedException.class, () -> controller.charges(null));
        }
        verifyNoInteractions(GATEWAY, ORDERS);
        for (String role : new String[] {"ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
            PmsReconcileService.Result body = controller.charges("2026-11-10");
            assertEquals(1, body.items().size());
            assertFalse(body.truncated());
        }
    }

    // ---- matching ----

    @Test
    void foundWhenFolioDateAndTotalMatch() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F100", "2026-11-10", "250.00"));
        PmsReconcileService.Result r = service.charges("2026-11-10");
        assertEquals(List.of(new ChargeCheck("o1", "Zed Quill", "R7", new BigDecimal("250.00"), "QUEUED", "FOUND")),
                r.items());
        assertFalse(r.stale());
        assertEquals(1_000, r.asOf());
    }

    @Test
    void foundOnAPostedItemSubtotalButNotOnAnUnconfirmedOne() {
        Order o = order("o1", "QUEUED", "F100", 350.0);
        o.setItems(new ArrayList<>(List.of(item("m1", 100), item("m2", 200), item("m3", 50))));
        o.setChargePostedItems(new ArrayList<>(List.of("m1#0", "m2#1")));
        orders(o);
        csv(HEADER + row("F100", "2026-11-10", "200"));
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));

        cacheClock.addAndGet(61_000);
        o.setChargePostedItems(new ArrayList<>(List.of("m1#0", "UNCONFIRMED:m3#2")));
        csv(HEADER + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("MISSING"), statuses("2026-11-10"));
    }

    @Test
    void checkedStatusIsMatchedLikeQueued() {
        orders(order("o1", "CHECKED", "F100", 99.5));
        csv(HEADER + row("F100", "2026-11-10", "99.50"));
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));
    }

    @Test
    void missingWhenTheCsvIsValidButHasNoRow() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER);
        assertEquals(List.of("MISSING"), statuses("2026-11-10"));
    }

    @Test
    void amountMismatchIsMissing() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F100", "2026-11-10", "249.99") + row("F100", "2026-11-10", "250.01"));
        assertEquals(List.of("MISSING"), statuses("2026-11-10"));
    }

    @Test
    void aRowOnAnotherFolioOrDayDoesNotMatch() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F1000", "2026-11-10", "250.00") + row("F10", "2026-11-10", "250.00")
                + row("F100", "2026-11-09", "250.00"));
        assertEquals(List.of("MISSING"), statuses("2026-11-10"));
    }

    @Test
    void unpostedFailedAndVoidedAreListedWithoutAnEzeeCall() {
        orders(order("o1", null, null, 10), order("o2", "FAILED", "F1", 20), order("o3", "VOIDED", "F1", 30));
        PmsReconcileService.Result r = service.charges("2026-11-10");
        assertEquals(List.of("UNPOSTED", "FAILED", "VOIDED"), r.items().stream().map(ChargeCheck::ourStatus).toList());
        assertEquals(List.of("N/A", "N/A", "N/A"), r.items().stream().map(ChargeCheck::eZeeStatus).toList());
        assertEquals("Dorm Q", r.items().get(0).room());
        assertEquals(Instant.parse("2026-11-10T06:00:00Z").toEpochMilli(), r.asOf());
        verifyNoInteractions(GATEWAY);
    }

    @Test
    void inProgressIsUnknownWithoutAnEzeeCall() {
        orders(order("o1", "IN_PROGRESS", "F1", 10));
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
        verifyNoInteractions(GATEWAY);
    }

    @Test
    void orderWithoutFolioIsUnknownAndNeedsNoCall() {
        orders(order("o1", "QUEUED", null, 10), order("o2", "QUEUED", " ", 10));
        assertEquals(List.of("UNKNOWN", "UNKNOWN"), statuses("2026-11-10"));
        verifyNoInteractions(GATEWAY);
    }

    // ---- CSV robustness ----

    @Test
    void gatewayFailureIsUnknownAndIsNotCached() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        when(GATEWAY.text(any(), eq("RetrieveBills"), any(), any())).thenThrow(new PmsUpstreamException("PMS down"));
        PmsReconcileService.Result r = service.charges("2026-11-10");
        assertEquals(List.of("UNKNOWN"), r.items().stream().map(ChargeCheck::eZeeStatus).toList());
        reset(GATEWAY);
        csv(HEADER + row("F100", "2026-11-10", "250.00"));
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));
    }

    @Test
    void busyGatewayIsUnknownToo() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        when(GATEWAY.text(any(), any(), any(), any())).thenThrow(new PmsBusyException("busy"));
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void htmlErrorPageIsUnknown() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv("<html><body>Error, \"F100\", 2026-11-10, 250.00</body></html>");
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void missingRequiredColumnIsUnknown() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv("\"Folio No\",\"Date\",\"Particular\",\"Total\"\r\n\"F100\",\"2026-11-10\",\"Food\",\"250.00\"\r\n");
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void emptyReplyIsUnknown() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv("");
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void bomReorderedColumnsQuotesAndThousandsSeparators() {
        orders(order("o1", "QUEUED", "F100", 1250.0));
        csv("\uFEFF amount ,\"Particular\",\"Note\",\"Guest Name\",\" FOLIO NO\",\"date\",\" TYPE \"\n"
                + "\"1,250.00\",\" food CHARGE \",\"Food, \"\"special\"\"\nline\",\"Quill, Zed \"\"Q\"\"\",\"F100\","
                + "\"2026-11-10\",\"EXTRA charges \"\n");
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));
    }

    @Test
    void tolerantDateFormats() {
        for (String d : new String[] {"2026-11-10", "10-11-2026", "10/11/2026", "10-Nov-2026", "10-NOV-2026",
                "2026-11-10 14:05:00"}) {
            cacheClock.addAndGet(61_000);
            orders(order("o1", "QUEUED", "F100", 250.0));
            csv(HEADER + row("F100", d, "250.00"));
            assertEquals(List.of("FOUND"), statuses("2026-11-10"), d);
        }
    }

    @Test
    void noParsableDateAtAllIsUnknown() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F100", "Nov 10th", "250.00") + row("F200", "", "10.00"));
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void anUndatedRowThatCouldBeTheChargeIsUnknownNotMissing() {
        orders(order("o1", "QUEUED", "F100", 250.0), order("o2", "QUEUED", "F200", 75.0));
        csv(HEADER + row("F100", "??", "250.00") + row("F200", "2026-11-10", "70.00"));
        assertEquals(List.of("UNKNOWN", "MISSING"), statuses("2026-11-10"));
    }

    @Test
    void staleBillsAreServedWithTheOldAsOf() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F100", "2026-11-10", "250.00"));
        service.charges("2026-11-10");
        cacheClock.addAndGet(61_000);
        reset(GATEWAY);
        when(GATEWAY.text(any(), any(), any(), any())).thenThrow(new PmsUpstreamException("PMS down"));
        PmsReconcileService.Result r = service.charges("2026-11-10");
        assertTrue(r.stale());
        assertEquals(1_000, r.asOf());
        assertEquals(List.of("FOUND"), r.items().stream().map(ChargeCheck::eZeeStatus).toList());
    }

    @Test
    void oneRowCannotProveTwoOrdersOnTheSameFolioDayAndAmount() {
        orders(order("o1", "QUEUED", "F100", 120.0), order("o2", "QUEUED", "F100", 120.0));
        csv(HEADER + row("F100", "2026-11-10", "120.00"));
        assertEquals(List.of("UNKNOWN", "UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void twoRowsProveTwoOrdersOnTheSameFolioDayAndAmount() {
        orders(order("o1", "QUEUED", "F100", 120.0), order("o2", "QUEUED", "F100", 120.0));
        csv(HEADER + row("F100", "2026-11-10", "120.00") + row("F100", "2026-11-10", "120"));
        assertEquals(List.of("FOUND", "FOUND"), statuses("2026-11-10"));
    }

    @Test
    void anItemSubtotalSharedWithAnotherOrdersTotalIsConsumedToo() {
        Order a = order("o1", "QUEUED", "F100", 150.0);
        a.setItems(new ArrayList<>(List.of(item("m1", 100), item("m2", 50))));
        a.setChargePostedItems(new ArrayList<>(List.of("m1#0", "m2#1")));
        orders(a, order("o2", "QUEUED", "F100", 100.0));
        csv(HEADER + row("F100", "2026-11-10", "100.00"));
        assertEquals(List.of("UNKNOWN", "UNKNOWN"), statuses("2026-11-10"));
    }

    private static Order twoFifties(String id) {
        Order x = order(id, "QUEUED", "F100", 100.0);
        x.setItems(new ArrayList<>(List.of(item("m1", 50), item("m1", 50))));
        x.setChargePostedItems(new ArrayList<>(List.of("m1#0", "m1#1")));
        return x;
    }

    @Test
    void repeatedItemAmountsWeighOneRowEach() {
        Order z = order("z", "QUEUED", "F100", 50.0);
        orders(twoFifties("x"), z);
        csv(HEADER + row("F100", "2026-11-10", "50.00") + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("UNKNOWN", "UNKNOWN"), statuses("2026-11-10"));
        cacheClock.addAndGet(61_000);
        csv(HEADER + row("F100", "2026-11-10", "50.00") + row("F100", "2026-11-10", "50.00")
                + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("FOUND", "FOUND"), statuses("2026-11-10"));
    }

    @Test
    void aLoneOrderWithTwoIdenticalItemsNeedsTwoRows() {
        orders(twoFifties("x"));
        csv(HEADER + row("F100", "2026-11-10", "50.00") + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));
        cacheClock.addAndGet(61_000);
        csv(HEADER + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
        cacheClock.addAndGet(61_000);
        csv(HEADER + row("F100", "2026-11-10", "100.00"));
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));
    }

    @Test
    void duplicatePostedEntryForOnePositionWeighsOnce() {
        Order x = order("x", "QUEUED", "F100", 50.0);
        x.setChargePostedItems(new ArrayList<>(List.of("m1#0", "m1#0")));
        orders(x);
        csv(HEADER + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("FOUND"), statuses("2026-11-10"));
    }

    @Test
    void anUnsharedAmountProvesTheOrderEvenWhenAnotherIsShortOfRows() {
        Order a = order("a", "QUEUED", "F100", 130.0);
        a.setItems(new ArrayList<>(List.of(item("m1", 100), item("m2", 30))));
        a.setChargePostedItems(new ArrayList<>(List.of("m1#0", "m2#1")));
        orders(a, order("b", "QUEUED", "F100", 100.0));
        csv(HEADER + row("F100", "2026-11-10", "100.00") + row("F100", "2026-11-10", "30.00"));
        assertEquals(List.of("FOUND", "UNKNOWN"), statuses("2026-11-10"));
    }

    private static Order posted(String id, String status, String... entries) {
        Order f = order(id, status, "F100", 80.0);
        f.setItems(new ArrayList<>(List.of(item("m1", 50), item("m2", 30))));
        f.setChargePostedItems(new ArrayList<>(List.of(entries)));
        return f;
    }

    @Test
    void aFailedOrdersPostedItemClaimsItsRow() {
        orders(posted("f", "FAILED", "m1#0"), order("z", "QUEUED", "F100", 50.0));
        csv(HEADER + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("N/A", "UNKNOWN"), statuses("2026-11-10"));
        cacheClock.addAndGet(61_000);
        csv(HEADER + row("F100", "2026-11-10", "50.00") + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("N/A", "FOUND"), statuses("2026-11-10"));
    }

    @Test
    void aVoidedOrderClaimsItsItemsAndTotal() {
        orders(posted("v", "VOIDED", "m1#0", "m2#1"), order("z", "QUEUED", "F100", 50.0),
                order("y", "QUEUED", "F100", 80.0));
        csv(HEADER + row("F100", "2026-11-10", "50.00") + row("F100", "2026-11-10", "80.00"));
        assertEquals(List.of("N/A", "UNKNOWN", "UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void anUnconfirmedItemCountsAsAPossibleRow() {
        orders(posted("f", "FAILED", "UNCONFIRMED:m1#0"), order("z", "QUEUED", "F100", 50.0));
        csv(HEADER + row("F100", "2026-11-10", "50.00"));
        assertEquals(List.of("N/A", "UNKNOWN"), statuses("2026-11-10"));
    }

    @Test
    void failedAndVoidedOnlyDayMakesNoGatewayCall() {
        orders(posted("f", "FAILED", "m1#0", "UNCONFIRMED:m2#1"), posted("v", "VOIDED", "m1#0"));
        assertEquals(List.of("N/A", "N/A"), statuses("2026-11-10"));
        verifyNoInteractions(GATEWAY);
    }

    @Test
    void negativeReversalRowsAreIgnored() {
        Order neg = order("o1", "QUEUED", "F100", -50.0);
        orders(neg);
        csv(HEADER + row("F100", "2026-11-10", "-50.00"));
        assertEquals(List.of("MISSING"), statuses("2026-11-10"));
    }

    @Test
    void paymentAndRoomRowsWithTheSameAmountDoNotCount() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F100", "2026-11-10", "250.00", "Bank", "UPI")
                + row("F100", "2026-11-10", "250.00", "Cash", "Cash")
                + row("F100", "2026-11-10", "250.00", "Room Charges", "Room Charges")
                + row("F100", "2026-11-10", "250.00", "Extra Charges", "Room Charges")
                + row("F100", "2026-11-10", "250.00", "Bank", "Food Charge"));
        assertEquals(List.of("MISSING"), statuses("2026-11-10"));
    }

    @Test
    void essentialChargeLineMatchesWithCaseAndSpaceVariants() {
        orders(order("o1", "QUEUED", "F100", 40.0), order("o2", "QUEUED", "F200", 60.0));
        csv(HEADER + row("F100", "2026-11-10", "40.00", "Extra Charges", "Essential")
                + row("F200", "2026-11-10", "60.00", "  extra CHARGES ", " FOOD charge "));
        assertEquals(List.of("FOUND", "FOUND"), statuses("2026-11-10"));
    }

    @Test
    void missingTypeColumnIsUnknown() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv("\"Folio No\",\"Date\",\"Particular\",\"Amount\"\r\n\"F100\",\"2026-11-10\",\"Food Charge\",\"250.00\"\r\n");
        assertEquals(List.of("UNKNOWN"), statuses("2026-11-10"));
    }

    // ---- window, cache, cap, dates ----

    @Test
    void oneCallCoversEveryPostingDayAndIsCached() {
        Order late = order("o2", "QUEUED", "F200", 80.0);
        late.setChargePostAt(ist(DAY.plusDays(1), 1));
        orders(order("o1", "QUEUED", "F100", 250.0), late);
        csv(HEADER + row("F100", "2026-11-10", "250.00") + row("F200", "2026-11-11", "80.00"));
        assertEquals(List.of("FOUND", "FOUND"), statuses("2026-11-10"));
        assertEquals(List.of("FOUND", "FOUND"), statuses("2026-11-10"));
        verify(GATEWAY, times(1)).text(EzeeFamily.FINANCE, "RetrieveBills",
                Map.of("fromdate", "2026-11-10", "todate", "2026-11-11"), PmsPriority.HUB);
    }

    @Test
    void aPostingFarOutsideTheWindowIsUnknown() {
        Order far = order("o2", "QUEUED", "F200", 80.0);
        far.setChargePostAt(ist(DAY.plusDays(45), 10));
        orders(order("o1", "QUEUED", "F100", 250.0), far);
        csv(HEADER + row("F100", "2026-11-10", "250.00"));
        assertEquals(List.of("FOUND", "UNKNOWN"), statuses("2026-11-10"));
        verify(GATEWAY).text(EzeeFamily.FINANCE, "RetrieveBills",
                Map.of("fromdate", "2026-11-10", "todate", "2026-12-10"), PmsPriority.HUB);
    }

    @Test
    void ordersAreSelectedByTheIstDayAndDefaultToToday() {
        orders();
        PmsReconcileService.Result r = service.charges(null);
        assertTrue(r.items().isEmpty());
        verify(ORDERS).findForChargeReconcile(ist(DAY, 0), ist(DAY.plusDays(1), 0), PmsReconcileService.MAX_ORDERS + 1);
        service.charges("2026-11-01");
        verify(ORDERS).findForChargeReconcile(ist(LocalDate.of(2026, 11, 1), 0), ist(LocalDate.of(2026, 11, 2), 0),
                PmsReconcileService.MAX_ORDERS + 1);
    }

    @Test
    void invalidDateIsA400() {
        assertThrows(IllegalArgumentException.class, () -> service.charges("10-11-2026"));
        assertThrows(IllegalArgumentException.class, () -> service.charges("2026-13-01"));
        verifyNoInteractions(ORDERS, GATEWAY);
    }

    @Test
    void capsAtFiveHundredAndSaysTruncated() {
        Order[] many = new Order[501];
        for (int i = 0; i < many.length; i++) {
            many[i] = order("o" + i, null, null, 1);
        }
        orders(many);
        PmsReconcileService.Result r = service.charges("2026-11-10");
        assertEquals(500, r.items().size());
        assertTrue(r.truncated());
        orders(order("o1", null, null, 1));
        assertFalse(service.charges("2026-11-10").truncated());
    }

    @Test
    void nullTotalGivesNullAmountAndJsonUsesTheFieldNames() throws Exception {
        Order o = order("o1", null, null, 1);
        o.setTotalAmount(null);
        orders(o);
        ChargeCheck c = service.charges("2026-11-10").items().get(0);
        assertEquals(null, c.amount());
        String json = new ObjectMapper().writeValueAsString(service.charges("2026-11-10"));
        assertTrue(json.contains("\"eZeeStatus\":\"N/A\""), json);
        assertTrue(json.contains("\"truncated\":false"), json);
        assertTrue(json.contains("\"asOf\""), json);
    }

    @Test
    void personalDataAndCsvContentNeverReachLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level old = root.getLevel();
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        root.setLevel(Level.ALL);
        root.addAppender(app);
        try {
            orders(order("o1", "QUEUED", "F9137", 250.0));
            csv(HEADER + row("F9137", "2026-11-10", "250.00"));
            service.charges("2026-11-10");
            cacheClock.addAndGet(61_000);
            reset(GATEWAY);
            when(GATEWAY.text(any(), any(), any(), any())).thenThrow(new PmsUpstreamException("PMS read failed"));
            service.charges("2026-11-10");
            service = new PmsReconcileService(ORDERS, GATEWAY, new PmsCache(cacheClock::get), new PmsProperties(),
                    CLOCK);
            service.charges("2026-11-10");
            assertFalse(app.list.isEmpty(), "the failure path should have logged something to inspect");
            for (ILoggingEvent e : app.list) {
                String m = e.getFormattedMessage() + e.getThrowableProxy();
                for (String pii : new String[] {"Zed", "Quill", "F9137", "R7", "Dorm Q", "Synthetic", "Food Charge"}) {
                    assertFalse(m.contains(pii), "log leaked " + pii + ": " + m);
                }
            }
        } finally {
            root.detachAppender(app);
            root.setLevel(old);
        }
    }

    @Test
    void cacheKeyHoldsDatesOnly() {
        orders(order("o1", "QUEUED", "F100", 250.0));
        csv(HEADER + row("F100", "2026-11-10", "250.00"));
        PmsReconcileService s = new PmsReconcileService(ORDERS, GATEWAY, new PmsCache(cacheClock::get) {
            @Override
            public <T> Cached<T> getOrLoad(String key, java.time.Duration ttl, java.util.concurrent.Callable<T> loader)
                    throws Exception {
                assertEquals("bills:2026-11-10:2026-11-10", key);
                assertEquals(java.time.Duration.ofSeconds(60), ttl);
                return super.getOrLoad(key, ttl, loader);
            }
        }, new PmsProperties(), CLOCK);
        assertEquals(List.of("FOUND"), s.charges("2026-11-10").items().stream().map(ChargeCheck::eZeeStatus).toList());
    }
}
