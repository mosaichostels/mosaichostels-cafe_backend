package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import com.hostel.ordering.pms.PmsDates;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Pushes for the cleaning list. Payloads carry room names and the cleaner's login only; a failed push never fails the request. */
@Component
public class CleaningNotifier {

    private static final Logger logger = LoggerFactory.getLogger(CleaningNotifier.class);

    private final UserRepository userRepository;
    private final FCMNotificationService fcm;

    private final Clock clock;

    @Autowired
    public CleaningNotifier(UserRepository userRepository, FCMNotificationService fcm) {
        this(userRepository, fcm, Clock.system(PmsDates.IST));
    }

    CleaningNotifier(UserRepository userRepository, FCMNotificationService fcm, Clock clock) {
        this.userRepository = userRepository;
        this.fcm = fcm;
        this.clock = clock;
    }

    public void listSubmitted(int added) {
        if (added <= 0) {
            return;
        }
        send("Cleaning list", added + " bed(s) to clean", "CLEANING_LIST", "ROLE_HOUSEKEEPING");
    }

    /** Combined submit wording; the caller decides whether anything is worth a push. */
    public void todaySubmitted(int added, int taskCount) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (added > 0) {
            parts.add(added + " bed(s)");
        }
        if (taskCount > 0) {
            parts.add(taskCount + " task(s)");
        }
        String body = parts.isEmpty() ? "Today's cleaning is ready" : "Today's cleaning: " + String.join(", ", parts);
        send("Cleaning list", body, "CLEANING_LIST", "ROLE_HOUSEKEEPING");
    }

    public void cleaned(String roomName, String cleaner, String ezee) {
        String body = roomName + " cleaned by " + cleaner + "."
                + ("CONFIRMED".equals(ezee) ? " eZee updated." : " eZee not updated: set it Clean in eZee.");
        send("Bed cleaned", body, "CLEANING_DONE", "ROLE_ADMIN", "ROLE_SUPERADMIN");
    }

    /** date is yyyy-MM-dd; shown only when it is not today (IST), i.e. a leftover ticked late. */
    public void taskDone(String displayName, String doneBy, String date) {
        String suffix = LocalDate.now(clock).toString().equals(date) ? "" : " (" + date + ")";
        send("Task done", displayName + " done by " + doneBy + suffix, "CLEANING_DONE", "ROLE_ADMIN", "ROLE_SUPERADMIN");
    }

    public void scheduleNotSubmitted() {
        send("Cleaning schedule", "Today's schedule has not been submitted.", "SCHEDULE_REMINDER",
                "ROLE_ADMIN", "ROLE_SUPERADMIN");
    }

    private void send(String title, String body, String type, String... roles) {
        try {
            Map<String, User> byId = new LinkedHashMap<>();
            for (String role : roles) {
                for (User u : userRepository.findByRolesContaining(role)) {
                    byId.putIfAbsent(u.getId() != null ? u.getId() : u.getUsername(), u);
                }
            }
            for (User u : byId.values()) {
                String token = u.getFcmToken();
                if (token == null || token.isBlank()) {
                    continue;
                }
                try {
                    fcm.sendNotificationToToken(token, title, body, Map.of("type", type));
                } catch (Exception e) {
                    logger.warn("{} push to {} failed: {}", type, u.getUsername(), e.toString());
                }
            }
        } catch (Exception e) {
            logger.warn("{} push failed: {}", type, e.toString());
        }
    }
}
