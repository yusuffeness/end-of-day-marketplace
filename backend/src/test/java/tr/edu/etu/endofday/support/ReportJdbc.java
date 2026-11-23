package tr.edu.etu.endofday.support;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A connection to the test container as the read-only reporting account eod_report, for tests only.
 *
 * <p>The application never uses this account. The connection exists so that tests can prove what eod_report is and is
 * not allowed to read: the reporting views, and no base table.
 *
 * <p>It is not a Spring bean and does not take part in test-managed transactions, so it sees committed data only.
 */
public final class ReportJdbc {

    private static final JdbcClient CLIENT = JdbcClient.create(new DriverManagerDataSource(
            MySqlTestContainer.jdbcUrl(), MySqlTestContainer.REPORT_USER, MySqlTestContainer.REPORT_PASSWORD));

    private ReportJdbc() {}

    public static JdbcClient client() {
        return CLIENT;
    }
}
