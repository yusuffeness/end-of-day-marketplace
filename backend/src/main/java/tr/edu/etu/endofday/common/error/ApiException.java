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
