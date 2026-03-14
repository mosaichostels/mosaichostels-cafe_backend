package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int limit;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public PagedResponse(List<T> content, int page, int limit, long totalElements) {
        this.content = content;
        this.page = page;
        this.limit = limit;
        this.totalElements = totalElements;
        this.totalPages = limit > 0 ? (int) Math.ceil((double) totalElements / limit) : 1;
        this.hasNext = page < this.totalPages - 1;
        this.hasPrevious = page > 0;
    }
}
