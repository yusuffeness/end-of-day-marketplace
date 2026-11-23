package tr.edu.etu.endofday.support;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A connection to the test container as the schema owner eod_owner, for schema tests only.
 *
 * <p>The application never uses this account: it connects as eod_app, which deliberately has no DELETE privilege
 * because the application does not physically delete users. Some schema rules, such as ON DELETE CASCADE and ON
 * DELETE RESTRICT on foreign keys, can only be proven by deleting rows, and the demo data file, which Flyway runs as
 * eod_owner in the dev profile, empties the tables before loading. This connection exists solely so that those tests
 * can run without widening the privileges of the application account.
 *
 * <p>It is not a Spring bean and does not take part in test-managed transactions: through {@link #client()} every
 * statement is committed immediately, so tests that use it must create their own data and remove it themselves. Tests
 * that need one transaction open a connection from {@link #dataSource()} and roll it back.
 */
public final class OwnerJdbc {

    private static final DataSource DATA_SOURCE = new DriverManagerDataSource(
            MySqlTestContainer.jdbcUrl(), MySqlTestContainer.OWNER_USER, MySqlTestContainer.OWNER_PASSWORD);

    private static final JdbcClient CLIENT = JdbcClient.create(DATA_SOURCE);

    private OwnerJdbc() {}

    public static JdbcClient client() {
        return CLIENT;
    }

    /** Opens a new, unpooled connection on every call. */
    public static DataSource dataSource() {
        return DATA_SOURCE;
    }
}
