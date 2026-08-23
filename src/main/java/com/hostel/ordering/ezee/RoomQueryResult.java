package com.hostel.ordering.ezee;

import java.util.List;
import java.util.Map;

public record RoomQueryResult(Map<String, String> fields, List<Map<String, String>> rows) {}
