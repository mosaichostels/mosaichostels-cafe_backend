package com.hostel.ordering.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.hostel.ordering.model.CleaningListEntry;
import com.hostel.ordering.pms.PmsFeatureDisabledException;
import com.hostel.ordering.pms.PmsNotEnabledException;
import com.hostel.ordering.pms.PmsRoomStatusWriteService;
import com.hostel.ordering.pms.PmsWriteResult;
import com.hostel.ordering.pms.dto.RoomStatusWriteRequest;
import com.hostel.ordering.pms.dto.RoomStatusWriteResult;
import com.hostel.ordering.repository.CleaningListRepository;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Bed cleaning list. Done order is fixed: atomically remove the entry, one eZee attempt, then notify.
 * The bed leaves the list whatever eZee does; a second Done finds nothing and is "already done".
 */
@Service
public class CleaningListService {

    private static final Logger logger = LoggerFactory.getLogger(CleaningListService.class);
    private static final Pattern ID = Pattern.compile("[0-9]{1,20}");
    private static final int MAX_NAME = 80;
    private static final int MAX_BEDS = 200;
    private static final Pattern DIGITS = Pattern.compile("\\d+|\\D+");

    public record BedInput(String roomId, String unitId, String roomName) {}

    public record SubmitResult(int added, int alreadyListed) {}

    /** ezee is CONFIRMED, NOT_CONFIRMED, OFF or FAILED; null when alreadyDone. */
    public record DoneResult(boolean alreadyDone, String ezee, String roomName) {}

    private final CleaningListRepository repository;
    private final MongoTemplate mongoTemplate;
    private final PmsRoomStatusWriteService roomStatusWrite;
    private final CleaningNotifier notifier;

    public CleaningListService(CleaningListRepository repository, MongoTemplate mongoTemplate,
            PmsRoomStatusWriteService roomStatusWrite, CleaningNotifier notifier) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.roomStatusWrite = roomStatusWrite;
        this.notifier = notifier;
    }

    public List<CleaningListEntry> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(CleaningListEntry::getRoomName, CleaningListService::natural)
                        .thenComparingLong(CleaningListEntry::getAddedAt))
                .toList();
    }

    public SubmitResult submit(List<BedInput> beds, String username) {
        if (beds == null || beds.isEmpty() || beds.size() > MAX_BEDS) {
            throw new IllegalArgumentException("A list holds 1 to " + MAX_BEDS + " beds");
        }
        for (BedInput b : beds) {
            validate(b);
        }
        int added = 0;
        int already = 0;
        for (BedInput b : beds) {
            if (repository.findByRoomIdAndUnitId(b.roomId(), b.unitId()).isPresent()) {
                already++;
                continue;
            }
            CleaningListEntry e = new CleaningListEntry();
            e.setRoomId(b.roomId());
            e.setUnitId(b.unitId());
            e.setRoomName(b.roomName().strip());
            e.setAddedBy(username);
            e.setAddedAt(System.currentTimeMillis());
            try {
                repository.save(e);
                added++;
            } catch (DuplicateKeyException race) {
                already++; // another submit won the unique index
            }
        }
        if (added > 0) {
            try {
                notifier.listSubmitted(added);
            } catch (RuntimeException ex) {
                logger.warn("Cleaning-list push failed: {}", ex.toString());
            }
        }
        return new SubmitResult(added, already);
    }

    public boolean remove(String id) {
        return mongoTemplate.findAndRemove(query(where("_id").is(id)), CleaningListEntry.class) != null;
    }

    public DoneResult done(String id, String username) {
        CleaningListEntry e = mongoTemplate.findAndRemove(query(where("_id").is(id)), CleaningListEntry.class);
        if (e == null) {
            return new DoneResult(true, null, null);
        }
        String ezee = writeClean(e, id, username);
        try {
            notifier.cleaned(e.getRoomName(), username, ezee);
        } catch (RuntimeException ex) {
            logger.warn("Cleaning-done push failed for {}: {}", e.getRoomName(), ex.toString());
        }
        return new DoneResult(false, ezee, e.getRoomName());
    }

    private String writeClean(CleaningListEntry e, String id, String username) {
        try {
            PmsWriteResult<RoomStatusWriteResult> r = roomStatusWrite.setStatus(e.getRoomId(),
                    new RoomStatusWriteRequest(e.getUnitId(), "CLEAN", "Cleaned by " + username),
                    "cleaning-done-" + id);
            return r.confirmed() ? "CONFIRMED" : "NOT_CONFIRMED";
        } catch (PmsFeatureDisabledException | PmsNotEnabledException off) {
            return "OFF";
        } catch (Exception ex) {
            logger.warn("eZee clean write failed for {}: {}", e.getRoomName(), ex.toString());
            return "FAILED";
        }
    }

    private static void validate(BedInput b) {
        if (b == null || b.roomId() == null || !ID.matcher(b.roomId()).matches()
                || b.unitId() == null || !ID.matcher(b.unitId()).matches()) {
            throw new IllegalArgumentException("Invalid room or unit id");
        }
        String n = b.roomName();
        if (n == null || n.isBlank() || n.length() > MAX_NAME || n.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Room name must be 1 to " + MAX_NAME + " characters, no control characters");
        }
    }

    /** Digit runs compare as numbers ("Dorm 2" before "Dorm 10"); other runs compare case-insensitively. */
    private static int natural(String a, String b) {
        var ma = DIGITS.matcher(a);
        var mb = DIGITS.matcher(b);
        while (ma.find() && mb.find()) {
            String x = ma.group();
            String y = mb.group();
            int c;
            if (Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))) {
                String xs = x.replaceFirst("^0+(?=.)", "");
                String ys = y.replaceFirst("^0+(?=.)", "");
                c = xs.length() != ys.length() ? Integer.compare(xs.length(), ys.length()) : xs.compareTo(ys);
            } else {
                c = x.compareToIgnoreCase(y);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.length(), b.length());
    }
}
