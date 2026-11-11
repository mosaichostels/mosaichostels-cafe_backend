package com.hostel.ordering.pms;

import static com.hostel.ordering.pms.PmsJson.elements;
import static com.hostel.ordering.pms.PmsJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.pms.dto.RoomStatusWriteRequest;
import com.hostel.ordering.pms.dto.RoomStatusWriteResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Marks an in-house room Clean or Dirty in eZee (UpdateRoomStatus). The first eZee write in the hub: off unless
 * {@code pms.writes.room-status} is true, canary-scoped by {@code pms.canary.room-status} (room ids), one write per
 * Idempotency-Key, and only "confirmed" when a fresh InhouseRoomStatus read shows the requested status.
 *
 * <p>The request body is built from the eZee doc ("Update Room Status") and has never run against live eZee.
 * Rooms that are blocked (out of order) may still be marked: the doc is silent, the write does not touch the block.
 */
@Service
public class PmsRoomStatusWriteService {

    static final String FEATURE = "room-status";

    private static final Pattern ID = Pattern.compile("[0-9]{1,20}");
    // same shape PmsWriteGuard enforces; checked here too so a bad key fails before any eZee read
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final int MAX_REMARKS = 200;

    private final PmsHousekeepingFeed feed;
    private final EzeeGateway gateway;
    private final PmsFlags flags;
    private final PmsWriteGuard guard;
    private final String hotelCode;

    public PmsRoomStatusWriteService(PmsHousekeepingFeed feed, EzeeGateway gateway, PmsFlags flags,
            PmsWriteGuard guard, @Value("${ezee.hotel-code:}") String hotelCode) {
        this.feed = feed;
        this.gateway = gateway;
        this.flags = flags;
        this.guard = guard;
        this.hotelCode = hotelCode;
    }

    public PmsWriteResult<RoomStatusWriteResult> setStatus(String roomId, RoomStatusWriteRequest req,
            String idempotencyKey) {
        if (req == null || roomId == null || !ID.matcher(roomId).matches()) {
            throw new IllegalArgumentException("Invalid room id");
        }
        String unitId = req.unitId();
        if (unitId == null || !ID.matcher(unitId).matches()) {
            throw new IllegalArgumentException("Invalid unit id");
        }
        String target = req.status() == null ? "" : req.status();
        if (!target.equals("CLEAN") && !target.equals("DIRTY")) {
            throw new IllegalArgumentException("Status must be CLEAN or DIRTY");
        }
        String remarks = req.remarks() == null ? "" : req.remarks().strip();
        if (remarks.length() > MAX_REMARKS || remarks.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Remarks must be at most 200 characters with no control characters");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (!KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key is invalid");
        }
        // Switch and canary come before the first eZee read: off or out of scope means zero eZee calls.
        flags.requireWrite(FEATURE);
        List<String> canary = flags.canary(FEATURE);
        // Fail closed: unlike the guard (empty = any target), a missing canary list refuses every room.
        if (!canary.contains(roomId)) {
            throw new PmsFeatureDisabledException("PMS write target is outside the canary");
        }

        JsonNode room = findRoom(feed.freshInhouseStatus(), roomId, unitId);
        if (room == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This room no longer matches eZee. Refresh the board and try again.");
        }
        String current = norm(room.path("hkstatus").asText(""));
        String label = target.equals("CLEAN") ? "Clean" : "Dirty";
        // Remarks make the request different from "already there", so they go through the write path.
        if (remarks.isEmpty() && current.equals(norm(label))) {
            return new PmsWriteResult<>(new RoomStatusWriteResult(roomId, unitId, target, label, true), true);
        }

        String from = current.equals("clean") ? "CLEAN" : current.equals("dirty") ? "DIRTY" : "UNSET";
        String hash = PmsWriteGuard.hash(roomId, unitId, from, target, remarks);
        String details = "roomId=" + roomId + " unitId=" + unitId + " from=" + from + " to=" + target
                + " remarksLength=" + remarks.length();
        AtomicReference<String> observed = new AtomicReference<>("");
        PmsWriteResult<RoomStatusWriteResult> out = guard.execute(FEATURE, roomId, idempotencyKey, hash,
                "ROOM_STATUS", details, RoomStatusWriteResult.class,
                () -> write(roomId, unitId, target, label, remarks),
                v -> {
                    JsonNode now = findRoom(feed.freshInhouseStatus(), roomId, unitId);
                    String hk = now == null ? "" : now.path("hkstatus").asText("");
                    observed.set(hk);
                    return norm(hk).equals(norm(label));
                });
        RoomStatusWriteResult v = out.value();
        if (v != null && !out.confirmed()) {
            v = new RoomStatusWriteResult(roomId, unitId, target, observed.get().strip(), false);
        }
        return new PmsWriteResult<>(v, out.confirmed());
    }

    /** Exactly one eZee POST. Body fields per the eZee doc; the success reply is {"status":"0","message":"Success"}. */
    private RoomStatusWriteResult write(String roomId, String unitId, String target, String label, String remarks) {
        // shortcut: per-row hotel_code is copied from the doc example, not confirmed required; settle at the live canary.
        Map<String, Object> row = Map.of("hotel_code", hotelCode, "roomid", roomId, "unitid", unitId,
                "hkstatus", label, "hkremarks", remarks);
        JsonNode reply = gateway.json(EzeeFamily.HOUSEKEEPING, "UpdateRoomStatus",
                Map.of("updatehkdata", List.of(row)), PmsPriority.HUB, PmsHousekeepingFeed.WRITE_WAIT);
        // The gateway does not know this endpoint's {"errorcode":"1"} shape, so it is checked here. Only an explicit
        // error or warning stops us; any other reply (even status != "0") is judged by the read-back.
        if (reply.has("errorcode") || (reply.path("warning").isTextual() && !text(reply, "warning").isEmpty())) {
            throw new PmsUpstreamException("PMS returned an error");
        }
        return new RoomStatusWriteResult(roomId, unitId, target, label, true);
    }

    private static JsonNode findRoom(JsonNode reply, String roomId, String unitId) {
        for (JsonNode r : elements(reply.path("roomlist"))) {
            if (text(r, "roomid").equals(roomId) && text(r, "unitid").equals(unitId)) {
                return r;
            }
        }
        return null;
    }

    private static String norm(String s) {
        return s.strip().toLowerCase(Locale.ROOT);
    }
}
