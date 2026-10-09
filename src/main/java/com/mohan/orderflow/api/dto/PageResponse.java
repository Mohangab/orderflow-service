package com.mohan.orderflow.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable, compact pagination envelope (Spring's PageImpl JSON is not a public contract). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
