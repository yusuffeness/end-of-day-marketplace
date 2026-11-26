package tr.edu.etu.endofday.payment.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.payment.PaymentStatus;
import tr.edu.etu.endofday.payment.PaymentTransaction;
import tr.edu.etu.endofday.payment.PaymentType;

/** Reads and writes ODEME_ISLEMI. */
@Repository
public class PaymentRepository {

    private static final String COLUMNS = "islem_id, siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum";

    private final JdbcClient jdbcClient;

    public PaymentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** The transaction recorded for a request id (istek_no is unique across charges and refunds). */
    public Optional<PaymentTransaction> findByRequestId(String requestId) {
        return jdbcClient
                .sql("SELECT " + COLUMNS + " FROM ODEME_ISLEMI WHERE istek_no = :requestId")
                .param("requestId", requestId)
                .query(PaymentRepository::map)
                .optional();
    }

    /** The successful charge of the order; the triggers allow at most one. */
    public Optional<PaymentTransaction> findSuccessfulCharge(long orderId) {
        return jdbcClient
                .sql("SELECT " + COLUMNS + """
                         FROM ODEME_ISLEMI
                        WHERE siparis_id = :orderId AND islem_turu = 'TAHSILAT' AND durum = 'BASARILI'
                        """)
                .param("orderId", orderId)
                .query(PaymentRepository::map)
                .optional();
    }

    /** Inserts a transaction and returns the generated islem_id. */
    public long insert(
            long orderId,
            Long sourceId,
            String requestId,
            PaymentType type,
            BigDecimal amount,
            PaymentStatus status,
            LocalDateTime at) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("""
                        INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum,
                                                  islem_zamani)
                        VALUES (:orderId, :sourceId, :requestId, :type, :amount, :status, :at)
                        """)
                .param("orderId", orderId)
                .param("sourceId", sourceId)
                .param("requestId", requestId)
                .param("type", type.name())
                .param("amount", amount)
                .param("status", status.name())
                .param("at", at)
                .update(keyHolder, "islem_id");
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    private static PaymentTransaction map(ResultSet rs, int rowNum) throws SQLException {
        long sourceId = rs.getLong("kaynak_islem_id");
        return new PaymentTransaction(
                rs.getLong("islem_id"),
                rs.getLong("siparis_id"),
                rs.wasNull() ? null : sourceId,
                rs.getString("istek_no"),
                PaymentType.valueOf(rs.getString("islem_turu")),
                rs.getBigDecimal("tutar"),
                PaymentStatus.valueOf(rs.getString("durum")));
    }
}
