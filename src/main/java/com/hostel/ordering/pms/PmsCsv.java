package com.hostel.ordering.pms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minimal RFC 4180 reader for eZee's CSV replies (no dependency). */
final class PmsCsv {

    private PmsCsv() {}

    /** A header cell compared by name: BOM dropped, trimmed, lower-cased. */
    static String headerName(String cell) {
        return cell.replace("﻿", "").strip().toLowerCase(Locale.ROOT);
    }

    /** Index of the first row that is not entirely blank, or rows.size() if none. */
    static int firstNonBlank(List<List<String>> rows) {
        int start = 0;
        while (start < rows.size() && rows.get(start).stream().allMatch(String::isBlank)) {
            start++;
        }
        return start;
    }

    /** RFC 4180: quoted fields may hold commas, line breaks and doubled quotes. */
    static List<List<String>> parse(String s) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c != '"') {
                    f.append(c);
                } else if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    f.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(f.toString());
                f.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(f.toString());
                f.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                f.append(c);
            }
        }
        if (f.length() > 0 || !row.isEmpty()) {
            row.add(f.toString());
            rows.add(row);
        }
        return rows;
    }
}
