package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** One bed on the cleaning list. The unique pair stops a bed being listed twice. */
@Document(collection = "cleaning_list")
@CompoundIndex(name = "room_unit_unique", def = "{'roomId': 1, 'unitId': 1}", unique = true)
public class CleaningListEntry {

    @Id
    private String id;
    private String roomId;
    private String unitId;
    private String roomName;
    private String addedBy;
    private long addedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }
}
