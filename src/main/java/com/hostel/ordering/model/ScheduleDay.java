package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A day whose housekeeping schedule the admin has submitted. Housekeeping sees nothing for a day without one. */
@Document(collection = "schedule_days")
public class ScheduleDay {

    @Id
    private String id;
    @Indexed(unique = true)
    private String date; // yyyy-MM-dd
    private String submittedBy;
    private long submittedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public long getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }
}
