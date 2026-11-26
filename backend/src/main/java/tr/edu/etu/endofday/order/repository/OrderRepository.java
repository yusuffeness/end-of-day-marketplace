package tr.edu.etu.endofday.order.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.order.LockedOrder;
import tr.edu.etu.endofday.order.OrderLine;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.OrderView;

/** Reads and writes SIPARIS and SIPARIS_KALEMI, and reads an order together with its pickup and review. */
@Repository
public class OrderRepository {

    /** One row per order with its totals, pickup and review; the items are read separately. */
    private static final String SELECT_VIEW = """
            SELECT s.siparis_id, s.musteri_id, s.isletme_id, i.isletme_adi, s.durum, s.olusturulma_zamani,
                   s.iptal_zamani,
                   (SELECT COALESCE(SUM(k.adet * k.birim_fiyat), 0)
                    FROM SIPARIS_KALEMI k WHERE k.siparis_id = s.siparis_id) AS toplam_tutar,
                   (SELECT COALESCE(SUM(o.tutar), 0)
                    FROM ODEME_ISLEMI o
                    WHERE o.siparis_id = s.siparis_id AND o.islem_turu = 'IADE' AND o.durum = 'BASARILI') AS iade_tutari,
                   t.teslim_kodu, t.teslim_baslangic, t.teslim_bitis, t.teslim_zamani,
                   d.puan, d.yorum, d.olusturulma_zamani AS degerlendirme_zamani
            FROM SIPARIS s
            JOIN ISLETME i ON i.isletme_id = s.isletme_id
            LEFT JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
            LEFT JOIN DEGERLENDIRME d ON d.siparis_id = s.siparis_id
            """;

    private static final String CUSTOMER_FILTER = """
            WHERE s.musteri_id = :customerId
              AND (:status IS NULL OR s.durum = :status)
            """;

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Inserts a confirmed order and returns the generated siparis_id. */
    public long insert(long customerId, long businessId, LocalDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("""
                        INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
                        VALUES (:customerId, :businessId, :createdAt, 'ONAYLANDI', NULL)
                        """)
                .param("customerId", customerId)
                .param("businessId", businessId)
                .param("createdAt", createdAt)
                .update(keyHolder, "siparis_id");
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    public void insertItem(long orderId, long listingId, int quantity, BigDecimal unitPrice) {
        jdbcClient
                .sql("""
                        INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
                        VALUES (:orderId, :listingId, :quantity, :unitPrice)
                        """)
                .param("orderId", orderId)
                .param("listingId", listingId)
                .param("quantity", quantity)
                .param("unitPrice", unitPrice)
                .update();
    }

    /** Reads the order with an exclusive lock, so that its status cannot change until the transaction ends. */
    public Optional<LockedOrder> lockById(long id) {
        return jdbcClient
                .sql("""
                        SELECT siparis_id, musteri_id, isletme_id, durum
                        FROM SIPARIS
                        WHERE siparis_id = :id
                        FOR UPDATE
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new LockedOrder(
                        rs.getLong("siparis_id"),
                        rs.getLong("musteri_id"),
                        rs.getLong("isletme_id"),
                        OrderStatus.valueOf(rs.getString("durum"))))
                .optional();
    }

    /**
     * Moves the order from one status to another. The condition on the current status makes the change happen at
     * most once.
     *
     * @return {@code true} when the order was in status {@code from} and was changed
     */
    public boolean changeStatus(long id, OrderStatus from, OrderStatus to, LocalDateTime cancelledAt) {
        int updated = jdbcClient
                .sql("""
                        UPDATE SIPARIS
                        SET durum = :to, iptal_zamani = :cancelledAt
                        WHERE siparis_id = :id AND durum = :from
                        """)
                .param("to", to.name())
                .param("cancelledAt", cancelledAt)
                .param("id", id)
                .param("from", from.name())
                .update();
        return updated == 1;
    }

    /** The order with its items. */
    public Optional<OrderView> findView(long id) {
        return jdbcClient
                .sql(SELECT_VIEW + " WHERE s.siparis_id = :id")
                .param("id", id)
                .query(OrderRepository::mapView)
                .optional()
                .map(view -> view.withItems(findLines(id)));
    }

    /** A page of the customer's orders without their items, newest first. */
    public List<OrderView> findViewsOfCustomer(long customerId, OrderStatus status, int limit, long offset) {
        return jdbcClient
                .sql(SELECT_VIEW + CUSTOMER_FILTER + """
                        ORDER BY s.olusturulma_zamani DESC, s.siparis_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("customerId", customerId)
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .param("offset", offset)
                .query(OrderRepository::mapView)
                .list();
    }

    public long countOfCustomer(long customerId, OrderStatus status) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM SIPARIS s " + CUSTOMER_FILTER)
                .param("customerId", customerId)
                .param("status", status == null ? null : status.name())
                .query(Long.class)
                .single();
    }

    /** The items of the order in listing id order. */
    public List<OrderLine> findLines(long orderId) {
        return jdbcClient
                .sql("""
                        SELECT k.ilan_id, p.baslik, k.adet, k.birim_fiyat
                        FROM SIPARIS_KALEMI k
                        JOIN PAKET_ILANI p ON p.ilan_id = k.ilan_id
                        WHERE k.siparis_id = :orderId
                        ORDER BY k.ilan_id
                        """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new OrderLine(
                        rs.getLong("ilan_id"),
                        rs.getString("baslik"),
                        rs.getInt("adet"),
                        rs.getBigDecimal("birim_fiyat")))
                .list();
    }

    private static OrderView mapView(ResultSet rs, int rowNum) throws SQLException {
        String code = rs.getString("teslim_kodu");
        OrderView.Pickup pickup = code == null
                ? null
                : new OrderView.Pickup(
                        code,
                        rs.getObject("teslim_baslangic", LocalDateTime.class),
                        rs.getObject("teslim_bitis", LocalDateTime.class),
                        rs.getObject("teslim_zamani", LocalDateTime.class));
        int rating = rs.getInt("puan");
        OrderView.Review review = rs.wasNull()
                ? null
                : new OrderView.Review(
                        rating, rs.getString("yorum"), rs.getObject("degerlendirme_zamani", LocalDateTime.class));
        return new OrderView(
                rs.getLong("siparis_id"),
                rs.getLong("musteri_id"),
                rs.getLong("isletme_id"),
                rs.getString("isletme_adi"),
                OrderStatus.valueOf(rs.getString("durum")),
                rs.getObject("olusturulma_zamani", LocalDateTime.class),
                rs.getObject("iptal_zamani", LocalDateTime.class),
                rs.getBigDecimal("toplam_tutar"),
                rs.getBigDecimal("iade_tutari"),
                pickup,
                review,
                List.of());
    }
}
