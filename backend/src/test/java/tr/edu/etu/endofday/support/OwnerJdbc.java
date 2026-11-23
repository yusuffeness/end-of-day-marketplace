package tr.edu.etu.endofday.support;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A connection to the test container as the schema owner eod_owner, for schema tests only.
 *
 * <p>The application never uses this account: it connects as eod_app, which deliberately has no DELETE privilege
 * because the application does not physically delete users. Some schema rules, such as ON DELETE CASCADE and ON
 * DELETE RESTRICT on foreign keys, can only be proven by deleting rows. This connection exists solely so that those
 * tests can exercise the schema's foreign key behaviour without widening the privileges of the application account.
 *
 * <p>It is not a Spring bean and does not take part in test-managed transactions: every statement is committed
 * immediately, so tests that use it must create their own data and remove it themselves.
 */
public final class OwnerJdbc {

    private static final JdbcClient CLIENT = JdbcClient.create(new DriverManagerDataSource(
            MySqlTestContainer.jdbcUrl(), MySqlTestContainer.OWNER_USER, MySqlTestContainer.OWNER_PASSWORD));

    private OwnerJdbc() {}

    public static JdbcClient client() {
        return CLIENT;
    }
}
