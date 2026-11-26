package tr.edu.etu.endofday.pickup.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.pickup.PickupRecord;

/** Reads and writes TESLIM_ALMA. */
@Repository
public class PickupRepository {

    private static final String SELECT_RECORD = """
            SELECT t.siparis_id, s.isletme_id, t.teslim_baslangic, t.teslim_bitis, t.teslim_zamani
            FROM TESLIM_ALMA t
            JOIN SIPARIS s ON s.siparis_id = t.siparis_id
            """;

    private final JdbcClient jdbcClient;

    public PickupRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Inserts the pickup record of a new order. A code that is already taken makes the statement fail with a
     * duplicate key error; the transaction itself stays usable, so the caller can try another code.
     */
    public void insert(long orderId, String code, LocalDateTime windowStart, LocalDateTime windowEnd) {
        jdbcClient
                .sql("""
                        INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis,
                                                 teslim_zamani, dogrulayan_id)
                        VALUES (:orderId, :code, :windowStart, :windowEnd, NULL, NULL)
                        """)
                .param("orderId", orderId)
                .param("code", code)
                .param("windowStart", windowStart)
                .param("windowEnd", windowEnd)
                .update();
    }

    public Optional<PickupRecord> findByOrderId(long orderId) {
        return jdbcClient
                .sql(SELECT_RECORD + " WHERE t.siparis_id = :orderId")
                .param("orderId", orderId)
                .query(PickupRepository::map)
                .optional();
    }

    public Optional<PickupRecord> findByCode(String code) {
        return jdbcClient
                .sql(SELECT_RECORD + " WHERE t.teslim_kodu = :code")
                .param("code", code)
                .query(PickupRepository::map)
                .optional();
    }

    /**
     * Records the pickup time and the verifying user together (CHECK on TESLIM_ALMA).
     *
     * @return {@code true} when the pickup had not been recorded before
     */
    public boolean markPickedUp(long orderId, LocalDateTime at, long verifierId) {
        int updated = jdbcClient
                .sql("""
                        UPDATE TESLIM_ALMA
                        SET teslim_zamani = :at, dogrulayan_id = :verifierId
                        WHERE siparis_id = :orderId AND teslim_zamani IS NULL
                        """)
                .param("at", at)
                .param("verifierId", verifierId)
                .param("orderId", orderId)
                .update();
        return updated == 1;
    }

    private static PickupRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new PickupRecord(
                rs.getLong("siparis_id"),
                rs.getLong("isletme_id"),
                rs.getObject("teslim_baslangic", LocalDateTime.class),
                rs.getObject("teslim_bitis", LocalDateTime.class),
                rs.getObject("teslim_zamani", LocalDateTime.class));
    }
}
