package com.cobre.notifications.infrastructure.web.dto.response;

import com.cobre.notifications.domain.model.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                result.page(), result.size(),
                result.totalElements(), result.totalPages()
        );
    }
}
