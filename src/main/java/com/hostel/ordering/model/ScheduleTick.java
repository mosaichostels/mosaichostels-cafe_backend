package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** One ticked task slot of a submitted day. The unique triple makes the first tick win. */
@Document(collection = "schedule_ticks")
@CompoundIndex(name = "date_task_slot_unique", def = "{'date': 1, 'taskKey': 1, 'slot': 1}", unique = true)
public class ScheduleTick {

    @Id
    private String id;
    private String date; // original day, yyyy-MM-dd
    private String taskKey;
    private String slot; // ONCE, AM or PM
    private String doneBy;
    private long doneAt;
    private String confirmedBy;
    private Long confirmedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public String getDoneBy() { return doneBy; }
    public void setDoneBy(String doneBy) { this.doneBy = doneBy; }
    public long getDoneAt() { return doneAt; }
    public void setDoneAt(long doneAt) { this.doneAt = doneAt; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public Long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Long confirmedAt) { this.confirmedAt = confirmedAt; }
}
