package tr.edu.etu.endofday.report.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The read-only connection pool of the reporting account eod_report (report section 7.3). eod_report may read the
 * reporting views only; every base table is denied to it by the database.
 *
 * <p>It is deliberately not a {@link javax.sql.DataSource} bean: the application's own data source (eod_app), Flyway
 * (eod_owner) and the transaction manager stay exactly as Spring Boot configures them.
 */
public final class ReportDatabase implements AutoCloseable {

    private static final int POOL_SIZE = 2;

    private final HikariDataSource dataSource;
    private final JdbcClient jdbcClient;

    public ReportDatabase(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("eod-report");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(0);
        this.dataSource = new HikariDataSource(config);
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public JdbcClient jdbcClient() {
        return jdbcClient;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public String toString() {
        return "ReportDatabase[pool=eod-report]";
    }
}
