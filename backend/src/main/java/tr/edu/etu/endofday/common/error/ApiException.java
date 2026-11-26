package tr.edu.etu.endofday.common.error;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * An error that is returned to the client as an RFC 9457 problem detail.
 *
 * <p>The {@code code} is a stable identifier such as {@code EOD-001} (see docs/error-codes.md) and the {@code detail} is
 * the user-facing message in Turkish.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;

    public ApiException(HttpStatus status, String code, String detail) {
        super(code + ": " + detail);
        this.status = Objects.requireNonNull(status, "status");
        this.code = Objects.requireNonNull(code, "code");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    /** The generic {@code EOD-404} error for a resource that does not exist or that the user may not see. */
    public static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, GlobalExceptionHandler.NOT_FOUND_CODE, GlobalExceptionHandler.NOT_FOUND_DETAIL);
    }

    /** The generic {@code EOD-403} error for an action the signed-in user may not perform. */
    public static ApiException forbidden() {
        return new ApiException(
                HttpStatus.FORBIDDEN, GlobalExceptionHandler.FORBIDDEN_CODE, GlobalExceptionHandler.FORBIDDEN_DETAIL);
    }

    /** The generic {@code EOD-400} error for an invalid parameter, without field errors. */
    public static ApiException badRequest() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                GlobalExceptionHandler.BAD_REQUEST_CODE,
                GlobalExceptionHandler.BAD_REQUEST_DETAIL);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }
}
