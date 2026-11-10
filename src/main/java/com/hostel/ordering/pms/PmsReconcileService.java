package com.hostel.ordering.pms;

import com.hostel.ordering.ezee.EzeeChargePostService;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.pms.dto.ChargeCheck;
import com.hostel.ordering.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Café charge reconciliation: each order of an IST day against eZee's Retrieve Bills CSV (FINANCE, RetrieveBills,
 * params fromdate/todate). Header columns seen live (controller spike, 2026-11-10): "Unique id","Hotel Name",
 * "Hotel Code","Folio No","Date","VoucherNo/ReceiptNo","Invoice Number","Guest Name","Bill To Name",
 * "Guest GST Number","State","Phone Number","Mobile Number","Type","Particular","Qty","Currency","Amount",... ;
 * one row per bill item. Live probe 2026-11-11: Date is ISO yyyy-MM-dd (other formats kept as a fallback); café
 * charges are Type "Extra Charges" with Particular "Food Charge" or "Essential"; only those rows are charge rows.
 *
 * <p>Match rule (Rulings P1-5, P1-6, P1-13), conservative so a charge is never falsely FOUND: FOUND only when a
 * charge row has the order's chargePostFolio, the IST day of chargePostAt, and an Amount equal to the order total or
 * one confirmed posted item's subtotal. FOUND means at least one charge line of the order was found. Rows are
 * consumed: each order claims an amount with a weight, max(number of its confirmed posted items with that subtotal,
 * 1 if its total equals it), since eZee holds one row per posted item. An amount on a folio and day proves FOUND
 * only if its rows are at least the summed weight of every order claiming it; fewer (but some) rows make those
 * claimants UNKNOWN. Claimants are limited to the orders listed for the day (selected by createdAt, capped at
 * 500), and negative (reversal) café rows are ignored because their shape was not probed, so a reversed (voided)
 * charge can still read FOUND: only its negative row is dropped, the positive one stays. Every listed order with a
 * folio and chargePostAt claims rows, whatever its chargePostStatus (a FAILED post can leave earlier items on the
 * folio, an UNCONFIRMED item may be there, a VOIDED order's positive rows stay), but only QUEUED/CHECKED orders are
 * looked up. A retry on a later day resets chargePostAt, so rows an earlier attempt posted on an earlier day are
 * claimed by nobody on that day (rare; can cause a false FOUND for another order on that folio and day). MISSING when the CSV
 * was valid and no such row exists. UNKNOWN when the CSV is unavailable or has an unexpected header, no row's date
 * parses, the order has no folio, or an undated charge row could be the charge.
 * Only folio numbers, days and amounts are kept from the CSV; names, rooms and folios are never logged.
 */
@Service
public class PmsReconcileService {

    static final int MAX_ORDERS = 500;
    static final String FOUND = "FOUND";
    static final String MISSING = "MISSING";
    static final String UNKNOWN = "UNKNOWN";
    static final String NOT_APPLICABLE = "N/A";
    private static final Set<String> CHECKED = Set.of("QUEUED", "CHECKED");
    private static final Set<String> NOT_POSTED = Set.of("FAILED", "VOIDED");
    // Café charge lines as seen in the live Retrieve Bills probe of 2026-11-11 (Ruling P1-13), compared lower-cased.
    static final String CHARGE_TYPE = "extra charges";
    static final String FOOD_PARTICULAR = "food charge";
    static final String ESSENTIAL_PARTICULAR = "essential";
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-uuuu")
                    .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
    private static final Logger log = LoggerFactory.getLogger(PmsReconcileService.class);

    public record Result(List<ChargeCheck> items, long asOf, boolean stale, boolean truncated) {}

    /**
     * The CSV reduced to what matching needs. valid=false: unexpected header. dated: folio -> day -> amount -> number
     * of charge rows; undated: folio -> amounts of charge rows whose date did not parse.
     */
    record Bills(boolean valid, int rows, int datedRows,
            Map<String, Map<LocalDate, Map<BigDecimal, Integer>>> dated,
            Map<String, Set<BigDecimal>> undated) {
        static final Bills INVALID = new Bills(false, 0, 0, Map.of(), Map.of());
    }

    private final OrderRepository orders;
    private final EzeeGateway gateway;
    private final PmsCache cache;
    private final PmsProperties props;
    private final Clock clock;

    @Autowired
    public PmsReconcileService(OrderRepository orders, EzeeGateway gateway, PmsCache cache, PmsProperties props) {
        this(orders, gateway, cache, props, Clock.systemUTC());
    }

    PmsReconcileService(OrderRepository orders, EzeeGateway gateway, PmsCache cache, PmsProperties props,
            Clock clock) {
        this.orders = orders;
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
        this.clock = clock;
    }

    /** Orders created on the IST day {@code date} (yyyy-MM-dd, blank = today); an invalid date is a 400. */
    public Result charges(String date) {
        LocalDate day = PmsDates.range(date, date, clock).from();
        long from = day.atStartOfDay(PmsDates.IST).toInstant().toEpochMilli();
        long to = day.plusDays(1).atStartOfDay(PmsDates.IST).toInstant().toEpochMilli();
        List<Order> found = orders.findForChargeReconcile(from, to, MAX_ORDERS + 1);
        boolean truncated = found.size() > MAX_ORDERS;
        List<Order> list = truncated ? found.subList(0, MAX_ORDERS) : found;

        // One call covers every posting day (a charge can be posted days after the order), capped at 30 days;
        // a posting outside the fetched window stays UNKNOWN rather than MISSING.
        LocalDate first = null;
        LocalDate last = null;
        for (Order o : list) {
            if (checkable(o)) {
                LocalDate d = postDay(o);
                first = first == null || d.isBefore(first) ? d : first;
                last = last == null || d.isAfter(last) ? d : last;
            }
        }
        Bills bills = null;
        long asOf = clock.millis();
        boolean stale = false;
        if (first != null) {
            if (last.isAfter(first.plusDays(PmsDates.MAX_RANGE_DAYS))) {
                last = first.plusDays(PmsDates.MAX_RANGE_DAYS);
            }
            Map<String, Object> params = Map.of("fromdate", first.toString(), "todate", last.toString());
            try {
                Cached<Bills> c = PmsReads.load(cache, "bills:" + first + ":" + last,
                        props.ttl("bills", Duration.ofSeconds(60)),
                        () -> parseBills(gateway.text(EzeeFamily.FINANCE, "RetrieveBills", params, PmsPriority.HUB)));
                bills = c.value();
                asOf = c.fetchedAtMs();
                stale = c.stale();
            } catch (RuntimeException e) {
                // Exception class only: messages and CSV content stay out of the log.
                log.warn("pms reconcile: Retrieve Bills unavailable ({}), charges reported UNKNOWN",
                        e.getClass().getSimpleName());
            }
        }

        // How many checked orders claim each folio/day/amount, so one row cannot prove two orders.
        Map<String, Integer> claimants = new HashMap<>();
        for (Order o : list) {
            if (o.getChargePostFolio() != null && !o.getChargePostFolio().isBlank() && o.getChargePostAt() != null) {
                claims(o).forEach((a, w) -> claimants.merge(claimKey(o, a), w, Integer::sum));
            }
        }
        List<ChargeCheck> items = new ArrayList<>(list.size());
        for (Order o : list) {
            String ours = o.getChargePostStatus();
            String theirs;
            if (ours == null) {
                ours = "UNPOSTED";
                theirs = NOT_APPLICABLE;
            } else if (NOT_POSTED.contains(ours)) {
                theirs = NOT_APPLICABLE;
            } else if (!CHECKED.contains(ours) || !checkable(o) || bills == null) {
                theirs = UNKNOWN;
            } else {
                theirs = match(o, bills, first, last, claimants);
            }
            String room = o.getChargePostRoom() != null && !o.getChargePostRoom().isBlank()
                    ? o.getChargePostRoom() : o.getDormitory();
            items.add(new ChargeCheck(o.getId(), o.getBookingName(), room, money(o.getTotalAmount()), ours, theirs));
        }
        return new Result(items, asOf, stale, truncated);
    }

    private static boolean checkable(Order o) {
        // Set.of rejects contains(null), hence the explicit null check
        return o.getChargePostStatus() != null && CHECKED.contains(o.getChargePostStatus())
                && o.getChargePostFolio() != null
                && !o.getChargePostFolio().isBlank() && o.getChargePostAt() != null;
    }

    private static LocalDate postDay(Order o) {
        return Instant.ofEpochMilli(o.getChargePostAt()).atZone(PmsDates.IST).toLocalDate();
    }

    private static String claimKey(Order o, BigDecimal amount) {
        return o.getChargePostFolio().strip() + "|" + postDay(o) + "|" + amount;
    }

    static String match(Order o, Bills bills, LocalDate first, LocalDate last, Map<String, Integer> claimants) {
        LocalDate day = postDay(o);
        if (!bills.valid() || (bills.rows() > 0 && bills.datedRows() == 0) || day.isBefore(first)
                || day.isAfter(last)) {
            return UNKNOWN;
        }
        Set<BigDecimal> wanted = candidates(o).keySet();
        String folio = o.getChargePostFolio().strip();
        Map<BigDecimal, Integer> onDay = bills.dated().getOrDefault(folio, Map.of()).getOrDefault(day, Map.of());
        boolean shared = false;
        for (BigDecimal a : wanted) {
            int rows = onDay.getOrDefault(a, 0);
            if (rows > 0 && rows >= claimants.get(claimKey(o, a))) {
                return FOUND;
            }
            shared |= rows > 0;
        }
        if (shared) {
            return UNKNOWN;
        }
        return bills.undated().getOrDefault(folio, Set.of()).stream().anyMatch(wanted::contains) ? UNKNOWN : MISSING;
    }

    /**
     * Amount -> claim weight: the subtotals of items confirmed posted ("menuItemId#index" entries, one eZee row each,
     * counted per distinct position) and the order total, which weighs max(item count, 1) for its amount.
     */
    static Map<BigDecimal, Integer> candidates(Order o) {
        return weights(o, false, true);
    }

    /**
     * Rows an order may have left on its folio, as amount -> weight: confirmed items, and UNCONFIRMED ones as possible
     * rows; the total too (max with items) for QUEUED/CHECKED, which match on it, and VOIDED, conservatively.
     * FAILED and IN_PROGRESS orders claim their items only.
     */
    static Map<BigDecimal, Integer> claims(Order o) {
        String st = o.getChargePostStatus();
        return weights(o, true, "VOIDED".equals(st) || (st != null && CHECKED.contains(st)));
    }

    // chargePostedItems holds "menuItemId#index" for an ok post and "UNCONFIRMED:menuItemId#index" for no reply.
    private static Map<BigDecimal, Integer> weights(Order o, boolean unconfirmed, boolean total) {
        Map<BigDecimal, Integer> out = new HashMap<>();
        Set<Integer> seen = new HashSet<>();
        List<OrderItem> items = o.getItems() == null ? List.of() : o.getItems();
        for (String entry : o.getChargePostedItems() == null ? List.<String>of() : o.getChargePostedItems()) {
            if (entry == null || (!unconfirmed && entry.startsWith(EzeeChargePostService.UNCONFIRMED_PREFIX))) {
                continue;
            }
            try {
                int idx = Integer.parseInt(entry.substring(entry.lastIndexOf('#') + 1));
                if (idx >= 0 && idx < items.size() && items.get(idx).getSubtotal() != null && seen.add(idx)) {
                    out.merge(money(items.get(idx).getSubtotal()), 1, Integer::sum);
                }
            } catch (NumberFormatException e) {
                // not an indexed entry: nothing to match on
            }
        }
        if (total && o.getTotalAmount() != null) {
            out.putIfAbsent(money(o.getTotalAmount()), 1); // max, not sum: the total is not a row of its own
        }
        return out;
    }

    static BigDecimal money(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** "1,250.00" -> 1250.00; anything unparsable -> null (the row cannot match). */
    static BigDecimal amount(String s) {
        try {
            return new BigDecimal(s.strip().replace(",", "")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** The leading date token in any accepted format, or null. */
    static LocalDate billDate(String s) {
        String token = s.strip().split("\\s+", 2)[0];
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(token, f);
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        return null;
    }

    static Bills parseBills(String csv) {
        List<List<String>> rows = PmsCsv.parse(csv == null ? "" : csv);
        int start = PmsCsv.firstNonBlank(rows);
        if (start == rows.size()) {
            return Bills.INVALID;
        }
        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(start);
        for (int i = 0; i < header.size(); i++) {
            col.putIfAbsent(PmsCsv.headerName(header.get(i)), i);
        }
        Integer folioCol = col.get("folio no");
        Integer dateCol = col.get("date");
        Integer amountCol = col.get("amount");
        Integer typeCol = col.get("type");
        Integer particularCol = col.get("particular");
        if (folioCol == null || dateCol == null || amountCol == null || typeCol == null || particularCol == null) {
            return Bills.INVALID;
        }
        Map<String, Map<LocalDate, Map<BigDecimal, Integer>>> dated = new HashMap<>();
        Map<String, Set<BigDecimal>> undated = new HashMap<>();
        int count = 0;
        int datedCount = 0;
        for (int r = start + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String folio = cell(row, folioCol).strip();
            if (folio.isEmpty()) {
                continue;
            }
            count++;
            BigDecimal amt = amount(cell(row, amountCol));
            LocalDate d = billDate(cell(row, dateCol));
            if (d != null) {
                datedCount++;
            }
            String particular = PmsCsv.headerName(cell(row, particularCol));
            if (amt == null || amt.signum() < 0 || !CHARGE_TYPE.equals(PmsCsv.headerName(cell(row, typeCol)))
                    || !(FOOD_PARTICULAR.equals(particular) || ESSENTIAL_PARTICULAR.equals(particular))) {
                continue;
            }
            if (d == null) {
                undated.computeIfAbsent(folio, k -> new HashSet<>()).add(amt);
            } else {
                dated.computeIfAbsent(folio, k -> new HashMap<>()).computeIfAbsent(d, k -> new HashMap<>())
                        .merge(amt, 1, Integer::sum);
            }
        }
        return new Bills(true, count, datedCount, dated, undated);
    }

    private static String cell(List<String> row, int i) {
        return i < row.size() ? row.get(i) : "";
    }
}
