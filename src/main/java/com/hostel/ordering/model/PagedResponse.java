package com.hostel.ordering.model;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Returned when the caller passes page/limit params; otherwise the plain list is returned.
 */
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

    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getLimit() { return limit; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isHasNext() { return hasNext; }
    public boolean isHasPrevious() { return hasPrevious; }
}
