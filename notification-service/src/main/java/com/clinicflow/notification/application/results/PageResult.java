package com.clinicflow.notification.application.results;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int number, int size, long totalElements) {
    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), number, size, totalElements);
    }
}
