package com.hostel.ordering.pms;

import com.hostel.ordering.pms.dto.BookingDetail;
import com.hostel.ordering.pms.dto.BookingSummary;
import com.hostel.ordering.pms.dto.GuestHit;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Front desk reads over live eZee data. Staff and admins; SuperAdmin passes via the role hierarchy. */
@RestController
@RequestMapping("/pms")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class PmsFrontDeskController {

    public record Listing(List<BookingSummary> items, long asOf, boolean stale) {
        static Listing of(Cached<List<BookingSummary>> c) {
            return new Listing(c.value(), c.fetchedAtMs(), c.stale());
        }
    }

    public record GuestListing(List<GuestHit> items, long asOf, boolean stale) {}

    public record Detail(BookingDetail item, long asOf, boolean stale) {}

    private final PmsFrontDeskService service;
    private final PmsBookingService bookings;
    private final PmsGuestService guests;
    private final Clock clock;

    @Autowired
    public PmsFrontDeskController(PmsFrontDeskService service, PmsBookingService bookings, PmsGuestService guests) {
        this(service, bookings, guests, Clock.systemUTC());
    }

    PmsFrontDeskController(PmsFrontDeskService service, PmsBookingService bookings, PmsGuestService guests, Clock clock) {
        this.service = service;
        this.bookings = bookings;
        this.guests = guests;
        this.clock = clock;
    }

    @GetMapping("/arrivals")
    public Listing arrivals(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        PmsDates.Range r = PmsDates.range(from, to, clock);
        return Listing.of(service.arrivals(r.from(), r.to()));
    }

    @GetMapping("/departures")
    public Listing departures(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        PmsDates.Range r = PmsDates.range(from, to, clock);
        return Listing.of(service.departures(r.from(), r.to()));
    }

    @GetMapping("/inhouse")
    public Listing inhouse() {
        return Listing.of(service.inHouse());
    }

    @GetMapping("/bookings/{bookingId}")
    public Detail booking(@PathVariable String bookingId) {
        Cached<BookingDetail> c = bookings.detail(bookingId);
        return new Detail(c.value(), c.fetchedAtMs(), c.stale());
    }

    @GetMapping("/guests")
    public GuestListing guests(@RequestParam(required = false) String q) {
        Cached<List<GuestHit>> c = guests.search(q);
        return new GuestListing(c.value(), c.fetchedAtMs(), c.stale());
    }
}
