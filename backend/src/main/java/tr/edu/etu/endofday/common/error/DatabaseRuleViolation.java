package tr.edu.etu.endofday.common.error;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Translates a business rule violation raised by a database trigger into an {@link ApiException}.
 *
 * <p>Triggers signal SQLSTATE {@code 45000} with a message of the form {@code EOD-0xx: <Turkish message>} (see
 * docs/error-codes.md). The code and the Turkish message are passed to the client unchanged.
 */
public final class DatabaseRuleViolation {

    static final String SIGNAL_SQL_STATE = "45000";

    /** EOD-008: only the owner of the order's business verifies the pickup. */
    private static final String FORBIDDEN_RULE = "EOD-008";

    private static final Pattern MESSAGE = Pattern.compile("^(EOD-[0-9]{3}): (.+)$", Pattern.DOTALL);

    private DatabaseRuleViolation() {}

    /** The rule violation somewhere in the cause chain of the exception, if there is one. */
    public static Optional<ApiException> from(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && SIGNAL_SQL_STATE.equals(sql.getSQLState())
                    && sql.getMessage() != null) {
                Matcher matcher = MESSAGE.matcher(sql.getMessage());
                if (matcher.matches()) {
                    String code = matcher.group(1);
                    HttpStatus status = FORBIDDEN_RULE.equals(code) ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
                    return Optional.of(new ApiException(status, code, matcher.group(2)));
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return Optional.empty();
    }
}
