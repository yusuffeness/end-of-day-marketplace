package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.dao.DataAccessException;

/** Assertions that identify exactly why MySQL rejected a statement. */
final class SqlErrorAssertions {

    static final int ER_TABLEACCESS_DENIED_ERROR = 1142;

    private static final String SIGNAL_SQL_STATE = "45000";

    private SqlErrorAssertions() {}

    /** Asserts that the statement is rejected by a trigger that raised the given business rule code (EOD-0xx). */
    static void assertViolatesRule(String ruleCode, ThrowingCallable statement) {
        assertIsRuleViolation(ruleCode, catchThrowable(statement));
    }

    /** Asserts that a failure is a trigger rejection with the given business rule code (EOD-0xx). */
    static void assertIsRuleViolation(String ruleCode, Throwable failure) {
        SQLException sqlException = asSqlException(failure);
        assertThat(sqlException.getSQLState()).isEqualTo(SIGNAL_SQL_STATE);
        assertThat(sqlException.getMessage()).startsWith(ruleCode + ": ");
    }

    /** Asserts that the statement fails with the given MySQL error code. */
    static void assertFailsWithErrorCode(int errorCode, ThrowingCallable statement) {
        assertThat(asSqlException(catchThrowable(statement)).getErrorCode()).isEqualTo(errorCode);
    }

    private static SQLException asSqlException(Throwable failure) {
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable cause = ((DataAccessException) failure).getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        return (SQLException) cause;
    }
}
