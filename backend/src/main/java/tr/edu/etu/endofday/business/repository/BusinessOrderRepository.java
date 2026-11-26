package tr.edu.etu.endofday.business.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.business.BusinessOrder;
import tr.edu.etu.endofday.order.OrderStatus;

/**
 * Reads the orders of a business by the day their pickup window starts. Only the first and last name of the customer
 * are read; email address, phone and pickup code are never selected.
 */
@Repository
public class BusinessOrderRepository {

    private static final String FROM_WHERE = """
            FROM SIPARIS s
            JOIN KULLANICI k ON k.kullanici_id = s.musteri_id
            JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
            WHERE s.isletme_id = :businessId
              AND t.teslim_baslangic >= :dayStart
              AND t.teslim_baslangic < :dayEnd
              AND (:status IS NULL OR s.durum = :status)
            """;

    private static final String SELECT = """
            SELECT s.siparis_id, s.durum, s.olusturulma_zamani, s.iptal_zamani, k.ad, k.soyad,
                   (SELECT COALESCE(SUM(sk.adet * sk.birim_fiyat), 0)
                    FROM SIPARIS_KALEMI sk WHERE sk.siparis_id = s.siparis_id) AS toplam_tutar,
                   (SELECT COALESCE(SUM(o.tutar), 0)
                    FROM ODEME_ISLEMI o
                    WHERE o.siparis_id = s.siparis_id AND o.islem_turu = 'IADE' AND o.durum = 'BASARILI') AS iade_tutari,
                   t.teslim_baslangic, t.teslim_bitis, t.teslim_zamani
            """ + FROM_WHERE + """
            ORDER BY t.teslim_baslangic, s.siparis_id
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT = "SELECT COUNT(*) " + FROM_WHERE;

    private final JdbcClient jdbcClient;

    public BusinessOrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<BusinessOrder> findByPickupDay(
            long businessId, LocalDateTime dayStart, OrderStatus status, int limit, long offset) {
        return jdbcClient
                .sql(SELECT)
                .param("businessId", businessId)
                .param("dayStart", dayStart)
                .param("dayEnd", dayStart.plusDays(1))
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .param("offset", offset)
                .query(BusinessOrderRepository::map)
                .list();
    }

    public long countByPickupDay(long businessId, LocalDateTime dayStart, OrderStatus status) {
        return jdbcClient
                .sql(COUNT)
                .param("businessId", businessId)
                .param("dayStart", dayStart)
                .param("dayEnd", dayStart.plusDays(1))
                .param("status", status == null ? null : status.name())
                .query(Long.class)
                .single();
    }

    private static BusinessOrder map(ResultSet rs, int rowNum) throws SQLException {
        return new BusinessOrder(
                rs.getLong("siparis_id"),
                OrderStatus.valueOf(rs.getString("durum")),
                rs.getObject("olusturulma_zamani", LocalDateTime.class),
                rs.getObject("iptal_zamani", LocalDateTime.class),
                rs.getString("ad"),
                rs.getString("soyad"),
                rs.getBigDecimal("toplam_tutar"),
                rs.getBigDecimal("iade_tutari"),
                rs.getObject("teslim_baslangic", LocalDateTime.class),
                rs.getObject("teslim_bitis", LocalDateTime.class),
                rs.getObject("teslim_zamani", LocalDateTime.class),
                List.of());
    }
}
