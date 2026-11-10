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
 * one row per bill item. The Date column's format was not seen, so several are accepted.
 *
 * <p>Match rule (Rulings P1-5, P1-6), conservative so a charge is never falsely FOUND: FOUND only when a row has the
 * order's chargePostFolio, the IST day of chargePostAt, and an Amount equal to the order total or one confirmed
 * posted item's subtotal. MISSING when the CSV was valid and no such row exists. UNKNOWN when the CSV is unavailable
 * or has an unexpected header, no row's date parses, the order has no folio, or an undated row could be the charge.
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
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-uuuu")
                    .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
    private static final Logger log = LoggerFactory.getLogger(PmsReconcileService.class);

    public record Result(List<ChargeCheck> items, long asOf, boolean stale, boolean truncated) {}

    /**
     * The CSV reduced to what matching needs. valid=false: unexpected header. dated: folio -> day -> amounts;
     * undated: folio -> amounts of rows whose date did not parse.
     */
    record Bills(boolean valid, int rows, int datedRows, Map<String, Map<LocalDate, Set<BigDecimal>>> dated,
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
                theirs = match(o, bills, first, last);
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

    static String match(Order o, Bills bills, LocalDate first, LocalDate last) {
        LocalDate day = postDay(o);
        if (!bills.valid() || (bills.rows() > 0 && bills.datedRows() == 0) || day.isBefore(first)
                || day.isAfter(last)) {
            return UNKNOWN;
        }
        Set<BigDecimal> wanted = candidates(o);
        String folio = o.getChargePostFolio().strip();
        Set<BigDecimal> onDay = bills.dated().getOrDefault(folio, Map.of()).getOrDefault(day, Set.of());
        if (onDay.stream().anyMatch(wanted::contains)) {
            return FOUND;
        }
        return bills.undated().getOrDefault(folio, Set.of()).stream().anyMatch(wanted::contains) ? UNKNOWN : MISSING;
    }

    /** The order total and the subtotals of items confirmed posted ("menuItemId#index" entries). */
    static Set<BigDecimal> candidates(Order o) {
        Set<BigDecimal> out = new HashSet<>();
        if (o.getTotalAmount() != null) {
            out.add(money(o.getTotalAmount()));
        }
        List<OrderItem> items = o.getItems() == null ? List.of() : o.getItems();
        for (String entry : o.getChargePostedItems() == null ? List.<String>of() : o.getChargePostedItems()) {
            if (entry == null || entry.startsWith(EzeeChargePostService.UNCONFIRMED_PREFIX)) {
                continue;
            }
            try {
                int idx = Integer.parseInt(entry.substring(entry.lastIndexOf('#') + 1));
                if (idx >= 0 && idx < items.size() && items.get(idx).getSubtotal() != null) {
                    out.add(money(items.get(idx).getSubtotal()));
                }
            } catch (NumberFormatException e) {
                // not an indexed entry: nothing to match on
            }
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
        if (folioCol == null || dateCol == null || amountCol == null || !col.containsKey("particular")) {
            return Bills.INVALID;
        }
        Map<String, Map<LocalDate, Set<BigDecimal>>> dated = new HashMap<>();
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
            if (amt == null) {
                continue;
            }
            if (d == null) {
                undated.computeIfAbsent(folio, k -> new HashSet<>()).add(amt);
            } else {
                dated.computeIfAbsent(folio, k -> new HashMap<>()).computeIfAbsent(d, k -> new HashSet<>()).add(amt);
            }
        }
        return new Bills(true, count, datedCount, dated, undated);
    }

    private static String cell(List<String> row, int i) {
        return i < row.size() ? row.get(i) : "";
    }
}
