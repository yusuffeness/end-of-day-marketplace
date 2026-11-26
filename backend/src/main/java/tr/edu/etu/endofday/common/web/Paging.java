package tr.edu.etu.endofday.common.web;

import tr.edu.etu.endofday.common.error.ApiException;

/**
 * The validated {@code page} and {@code size} query parameters of a list endpoint: {@code page} is zero-based,
 * {@code size} lies between 1 and 100.
 */
public record Paging(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * @throws ApiException {@code EOD-400} when a value is out of bounds
     */
    public static Paging of(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_SIZE) {
            throw ApiException.badRequest();
        }
        return new Paging(p, s);
    }

    public long offset() {
        return (long) page * size;
    }
}
