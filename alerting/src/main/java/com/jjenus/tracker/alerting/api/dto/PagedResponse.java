package com.jjenus.tracker.alerting.api.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public class PagedResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    public PagedResponse() {}

    public PagedResponse(Page<T> page) {
        this.content = page.getContent();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.first = page.isFirst();
        this.last = page.isLast();
        this.empty = page.isEmpty();
    }

    // Getters and Setters
    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isFirst() { return first; }
    public void setFirst(boolean first) { this.first = first; }

    public boolean isLast() { return last; }
    public void setLast(boolean last) { this.last = last; }

    public boolean isEmpty() { return empty; }
    public void setEmpty(boolean empty) { this.empty = empty; }
}
