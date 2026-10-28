package com.clinicflow.clinical.adapters.inbound.rest.dto;

import com.clinicflow.clinical.application.results.PageResult;

import java.util.List;
import java.util.function.Function;

// Same shape as Spring Data's PagedModel (content + page metadata), see ADR-029.
public record PageResponse<T>(List<T> content, PageMetadata page) {
    public record PageMetadata(int size, int number, long totalElements, int totalPages) {
    }

    public static <R, T> PageResponse<T> of(PageResult<R> result, Function<? super R, ? extends T> mapper) {
        return new PageResponse<>(result.items().stream().<T>map(mapper).toList(),
                new PageMetadata(result.size(), result.number(), result.totalElements(), result.totalPages()));
    }
}
