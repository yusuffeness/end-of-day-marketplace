package tr.edu.etu.endofday.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.OwnerJdbc;
import tr.edu.etu.endofday.support.TestAccounts;

/**
 * Trigger errors reach the client with their own code and Turkish message: a real SIGNAL raised by MySQL through the
 * application account is translated into a problem detail.
 */
class DatabaseRuleViolationIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void deleteTestData() {
        OwnerJdbc.client().sql("""
                        DELETE FROM SIPARIS WHERE musteri_id IN
                            (SELECT kullanici_id FROM KULLANICI WHERE eposta LIKE :pattern)
                        """).param("pattern", "%@" + TestAccounts.DOMAIN).update();
        OwnerJdbc.client().sql("""
                        DELETE FROM ISLETME WHERE yetkili_id IN
                            (SELECT kullanici_id FROM KULLANICI WHERE eposta LIKE :pattern)
                        """).param("pattern", "%@" + TestAccounts.DOMAIN).update();
        TestAccounts.deleteAll();
    }

    @Test
    void triggerSignalFromMySqlBecomesAProblemDetailWithItsCode() {
        long orderId = confirmedOrder();

        DataAccessException ex = catchThrowableOfType(
                DataAccessException.class,
                () -> jdbcClient
                        .sql("INSERT INTO DEGERLENDIRME (siparis_id, puan) VALUES (:orderId, 5)")
                        .param("orderId", orderId)
                        .update());
        ResponseEntity<ProblemDetail> response = new GlobalExceptionHandler().handleDataAccess(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry(GlobalExceptionHandler.CODE_PROPERTY, "EOD-006");
        assertThat(response.getBody().getDetail()).isEqualTo("Yalnızca teslim edilmiş sipariş değerlendirilebilir.");
    }

    @Test
    void pickupOwnershipRuleIsForbidden() {
        SQLException signal = new SQLException(
                "EOD-008: Teslimi yalnızca siparişin işletmesinin yetkilisi doğrulayabilir.", "45000", 1644);

        assertThat(DatabaseRuleViolation.from(new UncategorizedSQLException("insert", "INSERT", signal)))
                .hasValueSatisfying(error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getCode()).isEqualTo("EOD-008");
                });
    }

    @Test
    void otherDatabaseErrorsAreUnexpected() {
        SQLException other = new SQLException("Deadlock found when trying to get lock", "40001", 1213);
        UncategorizedSQLException ex = new UncategorizedSQLException("update", "UPDATE", other);

        assertThat(DatabaseRuleViolation.from(ex)).isEmpty();
        assertThat(new GlobalExceptionHandler().handleDataAccess(ex).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** A confirmed order inserted through the owner connection; the review trigger only reads its status. */
    private static long confirmedOrder() {
        JdbcClient owner = OwnerJdbc.client();
        owner.sql("""
                        INSERT INTO KULLANICI (ad, soyad, eposta, parola_ozeti)
                        VALUES ('Test', 'Kullanıcı', :email, 'x')
                        """).param("email", TestAccounts.uniqueEmail("tetik")).update();
        long userId = owner.sql("SELECT MAX(kullanici_id) FROM KULLANICI WHERE eposta LIKE :pattern")
                .param("pattern", "tetik.%@" + TestAccounts.DOMAIN)
                .query(Long.class)
                .single();
        owner.sql("""
                        INSERT INTO ISLETME (yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon)
                        VALUES (:userId, 'Tetik Fırını', 'FIRIN', 'Ankara', 'Çankaya', 'Adres', '0312 000 00 00')
                        """).param("userId", userId).update();
        owner.sql("""
                        INSERT INTO SIPARIS (musteri_id, isletme_id)
                        SELECT :userId, MAX(isletme_id) FROM ISLETME WHERE yetkili_id = :userId
                        """).param("userId", userId).update();
        return owner.sql("SELECT MAX(siparis_id) FROM SIPARIS WHERE musteri_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }
}
