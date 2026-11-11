package com.hostel.ordering.pms;

import com.hostel.ordering.pms.dto.RoomStatus;
import com.hostel.ordering.pms.dto.RoomStatusWriteRequest;
import com.hostel.ordering.pms.dto.RoomStatusWriteResult;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Housekeeping board read and the guarded clean/dirty write (flag off by default). Staff and admins; SuperAdmin passes via the role hierarchy. */
@RestController
@RequestMapping("/pms/housekeeping")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class PmsHousekeepingController {

    public record Rooms(List<RoomStatus> items, long asOf, boolean stale) {}

    private final PmsHousekeepingService service;

    private final PmsRoomStatusWriteService writes;

    public PmsHousekeepingController(PmsHousekeepingService service, PmsRoomStatusWriteService writes) {
        this.service = service;
        this.writes = writes;
    }

    @GetMapping("/rooms")
    public Rooms rooms() {
        Cached<List<RoomStatus>> c = service.rooms();
        return new Rooms(c.value(), c.fetchedAtMs(), c.stale());
    }

    @PostMapping("/rooms/{roomId}/status")
    public PmsWriteResult<RoomStatusWriteResult> setStatus(@PathVariable String roomId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RoomStatusWriteRequest body) {
        return writes.setStatus(roomId, body, idempotencyKey);
    }
}
