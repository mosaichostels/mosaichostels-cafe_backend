package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Who cleans which room on a departure day. Stores room ids and name snapshots only: never a booking id,
 * guest name or contact detail (those stay in eZee).
 */
@Document(collection = "cleaning_assignments")
public class CleaningAssignment {

    public enum Status { UNASSIGNED, ASSIGNED, IN_PROGRESS, DONE }

    public static final int MAX_NOTES = 300;

    @Id
    private String id;

    /** {@code departureDate|roomId}; unique so one task exists per room per day even under concurrent creates. */
    @Indexed(unique = true)
    private String taskKey;

    private String roomId;
    private String unitId;
    private String roomName;
    private String roomType;

    /** yyyy-MM-dd (stored as text to avoid time-zone conversion of a calendar day). */
    @Indexed
    private String departureDate;

    private String assigneeUsername;
    private Status status = Status.UNASSIGNED;
    private String notes;
    private Long createdAt;
    private Long updatedAt;

    @Version
    private Long version;

    public CleaningAssignment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getAssigneeUsername() { return assigneeUsername; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
