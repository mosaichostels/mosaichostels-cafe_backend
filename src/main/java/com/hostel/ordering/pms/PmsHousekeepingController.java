package com.hostel.ordering.pms;

import com.hostel.ordering.pms.dto.RoomStatus;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shared room feed used by the cleaning-list builder. Housekeeping and admins; SuperAdmin passes via the role hierarchy. */
@RestController
@RequestMapping("/pms/housekeeping")
@PreAuthorize("hasAnyRole('ADMIN','HOUSEKEEPING')")
public class PmsHousekeepingController {

    public record Rooms(List<RoomStatus> items, long asOf, boolean stale) {}

    private final PmsHousekeepingService service;

    public PmsHousekeepingController(PmsHousekeepingService service) {
        this.service = service;
    }

    @GetMapping("/rooms")
    public Rooms rooms() {
        Cached<List<RoomStatus>> c = service.rooms();
        return new Rooms(c.value(), c.fetchedAtMs(), c.stale());
    }
}
