package tr.edu.etu.endofday.support;

import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * A single MySQL container shared by all integration tests in the JVM.
 *
 * <p>The container reproduces the local Docker Compose setup: same image, same server flags and the same init script
 * that creates the end_of_day database and its accounts. The passwords below exist only inside this throwaway
 * container and are not secrets.
 */
public final class MySqlTestContainer {

    // Keep in sync with compose.yaml.
    static final String IMAGE = "mysql:8.4.13";

    public static final String OWNER_USER = "eod_owner";
    public static final String OWNER_PASSWORD = "test-owner-pass";
    public static final String APP_USER = "eod_app";
    public static final String APP_PASSWORD = "test-app-pass";
    public static final String REPORT_USER = "eod_report";
    public static final String REPORT_PASSWORD = "test-report-pass";
    static final String ROOT_PASSWORD = "test-root-pass";

    private static final int MYSQL_PORT = 3306;
    private static final String DATABASE = "end_of_day";
    private static final Path INIT_SCRIPT = Path.of("..", "database", "init", "01-create-database-and-accounts.sh");

    private static final GenericContainer<?> CONTAINER = createContainer();

    static {
        CONTAINER.start();
    }

    private MySqlTestContainer() {}

    /** JDBC URL with the same connection parameters as application.yml. */
    public static String jdbcUrl() {
        return "jdbc:mysql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(MYSQL_PORT) + "/" + DATABASE
                + "?characterEncoding=UTF-8&connectionCollation=utf8mb4_turkish_ci&connectionTimeZone=Europe/Istanbul";
    }

    @SuppressWarnings("resource") // The container lives for the whole JVM and is removed by Testcontainers.
    private static GenericContainer<?> createContainer() {
        return new GenericContainer<>(IMAGE)
                .withExposedPorts(MYSQL_PORT)
                .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
                .withEnv("EOD_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("EOD_APP_PASSWORD", APP_PASSWORD)
                .withEnv("EOD_REPORT_PASSWORD", REPORT_PASSWORD)
                // Keep in sync with the command section of compose.yaml.
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_turkish_ci",
                        "--default-time-zone=+03:00",
                        "--event-scheduler=ON",
                        "--local-infile=ON",
                        "--disable-log-bin")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_SCRIPT.toAbsolutePath().normalize(), 0755),
                        "/docker-entrypoint-initdb.d/" + INIT_SCRIPT.getFileName())
                // The entrypoint first runs a temporary server without networking (port: 0) to execute the init
                // scripts, then restarts. Only the final server listens on port 3306.
                .waitingFor(Wait.forLogMessage("(?s).*mysqld: ready for connections.*port: 3306\\s.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
    }
}
