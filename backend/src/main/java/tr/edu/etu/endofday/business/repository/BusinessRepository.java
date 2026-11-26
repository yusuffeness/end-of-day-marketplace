package tr.edu.etu.endofday.business.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.business.ApprovalStatus;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.BusinessType;

/** Reads ISLETME. */
@Repository
public class BusinessRepository {

    private static final String SELECT_BUSINESS = """
            SELECT isletme_id, yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon, onay_durumu, aktif_mi
            FROM ISLETME
            WHERE isletme_id = :id
            """;

    private final JdbcClient jdbcClient;

    public BusinessRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Business> findById(long id) {
        return jdbcClient
                .sql(SELECT_BUSINESS)
                .param("id", id)
                .query(BusinessRepository::map)
                .optional();
    }

    /**
     * Reads the business with a shared lock, so that its approval and active flag cannot change until the transaction
     * ends. Concurrent orders of the same business share the lock.
     */
    public Optional<Business> findByIdForShare(long id) {
        return jdbcClient
                .sql(SELECT_BUSINESS + " FOR SHARE")
                .param("id", id)
                .query(BusinessRepository::map)
                .optional();
    }

    private static Business map(ResultSet rs, int rowNum) throws SQLException {
        return new Business(
                rs.getLong("isletme_id"),
                rs.getLong("yetkili_id"),
                rs.getString("isletme_adi"),
                BusinessType.valueOf(rs.getString("isletme_turu")),
                rs.getString("sehir"),
                rs.getString("ilce"),
                rs.getString("adres"),
                rs.getString("telefon"),
                ApprovalStatus.valueOf(rs.getString("onay_durumu")),
                rs.getBoolean("aktif_mi"));
    }
}
