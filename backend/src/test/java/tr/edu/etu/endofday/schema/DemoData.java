package tr.edu.etu.endofday.schema;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tr.edu.etu.endofday.support.OwnerJdbc;

/**
 * Loads the dev demo data (db/seed/R__seed_demo_data.sql) for a test and removes it again.
 *
 * <p>The test profile never loads the demo data and all tests share one database, so the file is executed on a
 * dedicated eod_owner connection inside a single transaction, which is rolled back afterwards. eod_owner is the account
 * that runs the file through Flyway in the dev profile.
 */
final class DemoData {

    static final String SEED_FILE = "db/seed/R__seed_demo_data.sql";

    static final List<String> TABLES = List.of(
            "KULLANICI",
            "ROL",
            "KULLANICI_ROL",
            "ISLETME",
            "PAKET_ILANI",
            "SIPARIS",
            "SIPARIS_KALEMI",
            "ODEME_ISLEMI",
            "TESLIM_ALMA",
            "DEGERLENDIRME");

    private DemoData() {}

    /** Runs the check against a connection that sees the demo data, then rolls the demo data back. */
    static void withDemoData(Consumer<JdbcClient> check) {
        try (Connection connection = OwnerJdbc.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(
                        connection, new EncodedResource(new ClassPathResource(SEED_FILE), StandardCharsets.UTF_8));
                check.accept(JdbcClient.create(new SingleConnectionDataSource(connection, true)));
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load the demo data", e);
        }
    }

    private static final String ROW_COUNTS = """
            SELECT (SELECT COUNT(*) FROM KULLANICI) AS KULLANICI,
                   (SELECT COUNT(*) FROM ROL) AS ROL,
                   (SELECT COUNT(*) FROM KULLANICI_ROL) AS KULLANICI_ROL,
                   (SELECT COUNT(*) FROM ISLETME) AS ISLETME,
                   (SELECT COUNT(*) FROM PAKET_ILANI) AS PAKET_ILANI,
                   (SELECT COUNT(*) FROM SIPARIS) AS SIPARIS,
                   (SELECT COUNT(*) FROM SIPARIS_KALEMI) AS SIPARIS_KALEMI,
                   (SELECT COUNT(*) FROM ODEME_ISLEMI) AS ODEME_ISLEMI,
                   (SELECT COUNT(*) FROM TESLIM_ALMA) AS TESLIM_ALMA,
                   (SELECT COUNT(*) FROM DEGERLENDIRME) AS DEGERLENDIRME
            """;

    /** Row count of every schema table, in the order of {@link #TABLES}. */
    static Map<String, Long> rowCounts(JdbcClient client) {
        Map<String, Object> row = client.sql(ROW_COUNTS).query().singleRow();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            counts.put(table, ((Number) row.get(table)).longValue());
        }
        return counts;
    }
}
