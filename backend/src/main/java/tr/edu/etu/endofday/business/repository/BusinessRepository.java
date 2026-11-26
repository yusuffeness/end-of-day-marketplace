package tr.edu.etu.endofday.business.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.business.ApprovalStatus;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.BusinessApplication;
import tr.edu.etu.endofday.business.BusinessType;

/** Reads and writes ISLETME. */
@Repository
public class BusinessRepository {

    private static final String COLUMNS =
            "isletme_id, yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon, onay_durumu, aktif_mi";

    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM ISLETME WHERE isletme_id = :id";

    private final JdbcClient jdbcClient;

    public BusinessRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Business> findById(long id) {
        return jdbcClient
                .sql(SELECT_BY_ID)
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
                .sql(SELECT_BY_ID + " FOR SHARE")
                .param("id", id)
                .query(BusinessRepository::map)
                .optional();
    }

    /** Reads the business with an exclusive lock, for a change of its approval or active flag. */
    public Optional<Business> lockById(long id) {
        return jdbcClient
                .sql(SELECT_BY_ID + " FOR UPDATE")
                .param("id", id)
                .query(BusinessRepository::map)
                .optional();
    }

    /** Inserts an application: onay_durumu BEKLEMEDE and aktif_mi TRUE. Returns the generated isletme_id. */
    public long insertApplication(
            long ownerId, String name, BusinessType type, String city, String district, String address, String phone) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("""
                        INSERT INTO ISLETME (yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon,
                                             onay_durumu, aktif_mi)
                        VALUES (:ownerId, :name, :type, :city, :district, :address, :phone, 'BEKLEMEDE', TRUE)
                        """)
                .param("ownerId", ownerId)
                .param("name", name)
                .param("type", type.name())
                .param("city", city)
                .param("district", district)
                .param("address", address)
                .param("phone", phone)
                .update(keyHolder, "isletme_id");
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    /** The businesses of an owner in every approval state, newest first. */
    public List<Business> findByOwner(long ownerId, int limit, long offset) {
        return jdbcClient
                .sql("SELECT " + COLUMNS + """
                         FROM ISLETME
                        WHERE yetkili_id = :ownerId
                        ORDER BY isletme_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("ownerId", ownerId)
                .param("limit", limit)
                .param("offset", offset)
                .query(BusinessRepository::map)
                .list();
    }

    public long countByOwner(long ownerId) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM ISLETME WHERE yetkili_id = :ownerId")
                .param("ownerId", ownerId)
                .query(Long.class)
                .single();
    }

    /** Replaces the name, location and phone; type, approval and active flag stay. */
    public void updateDetails(long id, String name, String city, String district, String address, String phone) {
        jdbcClient
                .sql("""
                        UPDATE ISLETME
                        SET isletme_adi = :name, sehir = :city, ilce = :district, adres = :address, telefon = :phone
                        WHERE isletme_id = :id
                        """)
                .param("name", name)
                .param("city", city)
                .param("district", district)
                .param("address", address)
                .param("phone", phone)
                .param("id", id)
                .update();
    }

    public void setApprovalStatus(long id, ApprovalStatus status) {
        jdbcClient
                .sql("UPDATE ISLETME SET onay_durumu = :status WHERE isletme_id = :id")
                .param("status", status.name())
                .param("id", id)
                .update();
    }

    public void setActive(long id, boolean active) {
        jdbcClient
                .sql("UPDATE ISLETME SET aktif_mi = :active WHERE isletme_id = :id")
                .param("active", active)
                .param("id", id)
                .update();
    }

    /** Applications in one approval state with the owner's name, oldest first (isletme_id grows with time). */
    public List<BusinessApplication> findApplications(ApprovalStatus status, int limit, long offset) {
        return jdbcClient
                .sql("""
                        SELECT i.isletme_id, i.yetkili_id, i.isletme_adi, i.isletme_turu, i.sehir, i.ilce, i.adres,
                               i.telefon, i.onay_durumu, i.aktif_mi, k.ad, k.soyad
                        FROM ISLETME i
                        JOIN KULLANICI k ON k.kullanici_id = i.yetkili_id
                        WHERE i.onay_durumu = :status
                        ORDER BY i.isletme_id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status.name())
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) ->
                        new BusinessApplication(map(rs, rowNum), rs.getString("ad"), rs.getString("soyad")))
                .list();
    }

    public long countApplications(ApprovalStatus status) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM ISLETME WHERE onay_durumu = :status")
                .param("status", status.name())
                .query(Long.class)
                .single();
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
