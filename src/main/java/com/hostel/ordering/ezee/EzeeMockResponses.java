package com.hostel.ordering.ezee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Canned responses for local/dev use when ezee.mock=true — lets this
// integration be developed and its admin-search UI clicked through without
// live eZee credentials.
final class EzeeMockResponses {

    private EzeeMockResponses() {}

    static Map<String, String> forOprn(String oprn) {
        Map<String, String> result = new LinkedHashMap<>();
        if ("chargepost".equals(oprn)) {
            result.put("status", "ok");
            result.put("msg", "added in queue");
            result.put("requestid", "999999");
        } else if ("roomquery".equals(oprn)) {
            result.put("status", "ok");
            result.put("msg", "inhouse");
            result.put("guestname", "Mock Guest");
            result.put("room", "101");
            result.put("masterfolio", "8");
        } else {
            result.put("status", "ok");
            result.put("msg", "inhouse");
        }
        return result;
    }

    static List<Map<String, String>> roomRowsForOprn(String oprn) {
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("guestname", "Mock Guest");
        row1.put("room", "106");
        row1.put("masterfolio", "10");
        row1.put("roomtype", "8 - Bed Mixed Dorm");
        row1.put("resno", "1001");
        rows.add(row1);
        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("guestname", "Second Mock Guest");
        row2.put("room", "107");
        row2.put("masterfolio", "11");
        row2.put("roomtype", "8 - Bed Mixed Dorm");
        row2.put("resno", "1002");
        rows.add(row2);
        return rows;
    }
}
