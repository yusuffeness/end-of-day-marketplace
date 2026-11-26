package tr.edu.etu.endofday.common.web;

import java.util.List;
import java.util.function.Function;

/** One page of a list response, in the shape described under "Pagination" in docs/api.md. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <S, T> PageResponse<T> of(List<S> rows, Paging paging, long totalItems, Function<S, T> mapper) {
        return new PageResponse<>(rows.stream().map(mapper).toList(), paging.page(), paging.size(), totalItems);
    }
}
