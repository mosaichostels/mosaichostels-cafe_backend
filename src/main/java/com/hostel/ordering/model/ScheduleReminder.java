package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Marks that the "schedule not submitted" reminder went out for a day; the unique date makes the first run win. */
@Document(collection = "schedule_reminders")
public class ScheduleReminder {

    @Id
    private String id;
    @Indexed(unique = true)
    private String date; // yyyy-MM-dd
    private long sentAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public long getSentAt() { return sentAt; }
    public void setSentAt(long sentAt) { this.sentAt = sentAt; }
}
