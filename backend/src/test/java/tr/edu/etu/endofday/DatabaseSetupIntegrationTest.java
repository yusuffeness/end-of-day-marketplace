package tr.edu.etu.endofday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tr.edu.etu.endofday.support.IntegrationTest;

class DatabaseSetupIntegrationTest extends IntegrationTest {

    private static final int ER_TABLEACCESS_DENIED_ERROR = 1142;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private Clock clock;

    @Test
    void applicationConnectsAsTheAppAccount() {
        String currentUser =
                jdbcClient.sql("SELECT CURRENT_USER()").query(String.class).single();

        assertThat(currentUser).isEqualTo("eod_app@%");
    }

    @Test
    void serverUsesTheConfiguredCharacterSetCollationTimeZoneAndEventScheduler() {
        Map<String, Object> settings = jdbcClient
                .sql("SELECT @@character_set_server AS character_set_server,"
                        + " @@collation_server AS collation_server,"
                        + " @@time_zone AS time_zone,"
                        + " @@event_scheduler AS event_scheduler")
                .query()
                .singleRow();

        assertThat(settings)
                .containsEntry("character_set_server", "utf8mb4")
                .containsEntry("collation_server", "utf8mb4_turkish_ci")
                .containsEntry("time_zone", "+03:00")
                .containsEntry("event_scheduler", "ON");
    }

    @Test
    void flywayCreatesItsSchemaHistoryTable() {
        Integer tables = jdbcClient
                .sql("SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = :schema AND TABLE_NAME = :table")
                .param("schema", "end_of_day")
                .param("table", "flyway_schema_history")
                .query(Integer.class)
                .single();

        assertThat(tables).isEqualTo(1);
    }

    @Test
    void appAccountIsNotAllowedToCreateTables() {
        assertThatThrownBy(() -> jdbcClient
                        .sql("CREATE TABLE end_of_day.t_privilege_check (id INT)")
                        .update())
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause())
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                sqlException -> assertThat(sqlException.getErrorCode())
                                        .isEqualTo(ER_TABLEACCESS_DENIED_ERROR)));
    }

    @Test
    void connectionCollationSortsTurkishLettersCorrectly() {
        List<String> names = jdbcClient
                .sql("SELECT ad FROM (SELECT 'zeytin' AS ad UNION ALL SELECT 'İzmir' UNION ALL SELECT 'ışık'"
                        + " UNION ALL SELECT 'çörek' UNION ALL SELECT 'Ağaç' UNION ALL SELECT 'Cam'"
                        + " UNION ALL SELECT 'Ihlamur') t ORDER BY ad")
                .query(String.class)
                .list();

        assertThat(names).containsExactly("Ağaç", "Cam", "çörek", "Ihlamur", "ışık", "İzmir", "zeytin");
    }

    @Test
    void databaseTimeMatchesTheApplicationClock() {
        LocalDateTime databaseNow =
                jdbcClient.sql("SELECT NOW()").query(LocalDateTime.class).single();

        assertThat(databaseNow).isCloseTo(LocalDateTime.now(clock), within(5, ChronoUnit.SECONDS));
    }
}
