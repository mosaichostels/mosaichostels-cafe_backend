package com.hostel.ordering.service;

import com.hostel.ordering.model.ScheduleReminder;
import com.hostel.ordering.pms.PmsDates;
import com.hostel.ordering.repository.ScheduleReminderRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Pushes Admin and Superadmin once a day if today's schedule is still not submitted. */
@Service
public class ScheduleReminderTask {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleReminderTask.class);

    private final ScheduleService schedule;
    private final ScheduleReminderRepository reminders;
    private final CleaningNotifier notifier;
    private final Clock clock;

    @Autowired
    public ScheduleReminderTask(ScheduleService schedule, ScheduleReminderRepository reminders,
            CleaningNotifier notifier) {
        this(schedule, reminders, notifier, Clock.system(PmsDates.IST));
    }

    public ScheduleReminderTask(ScheduleService schedule, ScheduleReminderRepository reminders,
            CleaningNotifier notifier, Clock clock) {
        this.schedule = schedule;
        this.reminders = reminders;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Every 10 minutes from 9:00 to 11:59 IST: the later runs catch a Space that woke up late. The unique row makes the first run win. */
    @Scheduled(cron = "0 */10 9-11 * * *", zone = "Asia/Kolkata")
    public void run() {
        String today = LocalDate.now(clock.withZone(PmsDates.IST)).toString();
        if (schedule.isSubmitted(today)) {
            return;
        }
        ScheduleReminder row = new ScheduleReminder();
        row.setDate(today);
        row.setSentAt(clock.millis());
        try {
            reminders.insert(row);
        } catch (DuplicateKeyException e) {
            return;
        }
        try {
            notifier.scheduleNotSubmitted();
        } catch (Exception e) {
            logger.warn("Schedule reminder push failed: {}", e.toString());
        }
    }
}
