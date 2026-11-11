package com.hostel.ordering.pms.dto;

import java.util.List;

/** The cleaning to-do for one day; {@code unmatched} counts departures whose room name is blank, not in the room list, or shared by several rooms (ambiguous). */
public record CleaningTaskList(List<CleaningTask> items, List<Unmatched> unmatched, long asOf, boolean stale) {

    public record Unmatched(String roomName, int count, boolean ambiguous) {}
}
