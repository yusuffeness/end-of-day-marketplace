package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.edu.etu.endofday.schema.SchemaTestData.MISSING_ID;
import static tr.edu.etu.endofday.schema.SqlErrorAssertions.ER_TABLEACCESS_DENIED_ERROR;
import static tr.edu.etu.endofday.schema.SqlErrorAssertions.assertFailsWithErrorCode;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.ReportJdbc;

/**
 * Proves the privileges of the application account eod_app and the reporting account eod_report (init script and
 * V5__grant_table_privileges.sql).
 *
 * <p>Only the test that changes data runs in a transaction. The refused DDL statements run outside one, because MySQL
 * commits implicitly around DDL statements.
 */
class PrivilegesIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    // eod_app

    @Test
    @Transactional
    void appAccountCanDeleteRoleAssignments() {
        SchemaTestData data = new SchemaTestData(jdbcClient);
        long userId = data.createUser();
        long roleId = data.roleId("MUSTERI");
        data.insertUserRole(data.userRoleRow(userId, roleId));

        int deleted = jdbcClient
                .sql("DELETE FROM KULLANICI_ROL WHERE kullanici_id = :kullanici AND rol_id = :rol")
                .params(Map.of("kullanici", userId, "rol", roleId))
                .update();

        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void appAccountCannotDeleteOrders() {
        assertFailsWithErrorCode(
                ER_TABLEACCESS_DENIED_ERROR,
                () -> jdbcClient
                        .sql("DELETE FROM SIPARIS WHERE siparis_id = :id")
                        .param("id", MISSING_ID)
                        .update());
    }

    @Test
    void appAccountCannotDropTables() {
        assertFailsWithErrorCode(
                ER_TABLEACCESS_DENIED_ERROR,
                () -> jdbcClient.sql("DROP TABLE SIPARIS").update());
    }

    @Test
    void appAccountCannotAlterTables() {
        assertFailsWithErrorCode(
                ER_TABLEACCESS_DENIED_ERROR,
                () -> jdbcClient
                        .sql("ALTER TABLE SIPARIS ADD COLUMN yetki_denemesi INT")
                        .update());
    }

    @Test
    void appAccountCannotGrantPrivileges() {
        assertFailsWithErrorCode(
                ER_TABLEACCESS_DENIED_ERROR,
                () -> jdbcClient
                        .sql("GRANT SELECT ON end_of_day.SIPARIS TO 'eod_report'@'%'")
                        .update());
    }

    // eod_report

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "SELECT COUNT(*) FROM v_order_summary",
                "SELECT COUNT(*) FROM v_business_sales",
                "SELECT COUNT(*) FROM v_payment_summary",
                "SELECT COUNT(*) FROM v_review_summary",
                "SELECT COUNT(*) FROM v_business_status",
                "SELECT COUNT(*) FROM v_user_registrations"
            })
    void reportAccountCanReadEveryReportView(String query) {
        Long rows = ReportJdbc.client().sql(query).query(Long.class).single();

        assertThat(rows).isNotNull().isNotNegative();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SELECT COUNT(*) FROM KULLANICI", "SELECT COUNT(*) FROM SIPARIS"})
    void reportAccountCannotReadBaseTables(String query) {
        assertFailsWithErrorCode(
                ER_TABLEACCESS_DENIED_ERROR,
                () -> ReportJdbc.client().sql(query).query(Long.class).single());
    }
}
