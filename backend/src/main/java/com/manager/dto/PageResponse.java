package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    /**
     * Slice an in-memory list into a page. Firestore Admin SDK has no offset()
     * support, so we fetch all docs and slice here. Acceptable for orgs with
     * < ~5 000 records per collection; revisit with cursor pagination if needed.
     */
    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        if (size <= 0) size = 20;
        if (page < 0) page = 0;

        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        return new PageResponse<>(
                all.subList(from, to),
                page,
                size,
                total,
                totalPages,
                page == 0,
                page >= totalPages - 1 || totalPages == 0
        );
    }
}
