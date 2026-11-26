package tr.edu.etu.endofday.listing.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.business.ApprovalStatus;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.BusinessType;
import tr.edu.etu.endofday.listing.ListingDetail;
import tr.edu.etu.endofday.listing.ListingFilter;
import tr.edu.etu.endofday.listing.ListingStatus;
import tr.edu.etu.endofday.listing.ListingSummary;
import tr.edu.etu.endofday.listing.LockedListing;
import tr.edu.etu.endofday.listing.NewListing;

/** Reads and writes PAKET_ILANI. */
@Repository
public class ListingRepository {

    /**
     * The purchasable listings: active, with stock left, of an approved and active business, and with a pickup window
     * that has not started. Every filter is optional; a NULL parameter does not filter.
     */
    private static final String SEARCH_FROM_WHERE = """
            FROM PAKET_ILANI p
            JOIN ISLETME i ON i.isletme_id = p.isletme_id
            WHERE p.durum = 'AKTIF'
              AND p.kalan_adet > 0
              AND p.teslim_baslangic > :now
              AND i.onay_durumu = 'ONAYLANDI'
              AND i.aktif_mi
              AND (:city IS NULL OR i.sehir = :city)
              AND (:district IS NULL OR i.ilce = :district)
              AND (:type IS NULL OR i.isletme_turu = :type)
              AND (:minPrice IS NULL OR p.satis_fiyati >= :minPrice)
              AND (:maxPrice IS NULL OR p.satis_fiyati <= :maxPrice)
            """;

    private static final String SEARCH = """
            SELECT p.ilan_id, p.isletme_id, i.isletme_adi, i.isletme_turu, i.sehir, i.ilce, p.baslik,
                   p.satis_fiyati, p.normal_fiyat, p.kalan_adet, p.teslim_baslangic, p.teslim_bitis
            """ + SEARCH_FROM_WHERE + """
            ORDER BY p.teslim_baslangic, p.ilan_id
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT = "SELECT COUNT(*) " + SEARCH_FROM_WHERE;

    private static final String DETAIL_COLUMNS = """
            SELECT p.ilan_id, p.baslik, p.aciklama, p.alerjen_bilgisi, p.normal_fiyat, p.satis_fiyati, p.toplam_adet,
                   p.kalan_adet, p.teslim_baslangic, p.teslim_bitis, p.durum, p.olusturulma_zamani,
                   i.isletme_id, i.yetkili_id, i.isletme_adi, i.isletme_turu, i.sehir, i.ilce, i.adres, i.telefon,
                   i.onay_durumu, i.aktif_mi
            """;

    private static final String DETAIL_FROM = """
            FROM PAKET_ILANI p
            JOIN ISLETME i ON i.isletme_id = p.isletme_id
            """;

    private static final String SELECT_DETAIL = DETAIL_COLUMNS + DETAIL_FROM + "WHERE p.ilan_id = :id";

    /** Every listing, active or not, for the owner's and the administrator's lists; every filter is optional. */
    private static final String INSPECTION_WHERE = """
            WHERE (:businessId IS NULL OR p.isletme_id = :businessId)
              AND (:status IS NULL OR p.durum = :status)
              AND (:from IS NULL OR p.teslim_baslangic >= :from)
              AND (:to IS NULL OR p.teslim_baslangic < :to)
            """;

    private static final String INSPECTION = DETAIL_COLUMNS + DETAIL_FROM + INSPECTION_WHERE + """
            ORDER BY p.olusturulma_zamani DESC, p.ilan_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String INSPECTION_COUNT = "SELECT COUNT(*) " + DETAIL_FROM + INSPECTION_WHERE;

    private final JdbcClient jdbcClient;

    public ListingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<ListingSummary> findPurchasable(ListingFilter filter, LocalDateTime now, int limit, long offset) {
        return withFilter(jdbcClient.sql(SEARCH), filter, now)
                .param("limit", limit)
                .param("offset", offset)
                .query(ListingRepository::mapSummary)
                .list();
    }

    public long countPurchasable(ListingFilter filter, LocalDateTime now) {
        return withFilter(jdbcClient.sql(COUNT), filter, now).query(Long.class).single();
    }

    public Optional<ListingDetail> findDetail(long id) {
        return jdbcClient
                .sql(SELECT_DETAIL)
                .param("id", id)
                .query(ListingRepository::mapDetail)
                .optional();
    }

    /**
     * Reads the listings with exclusive locks taken in ascending id order. Every transaction that locks several
     * listings uses this order, so two orders can never wait for each other's listings (no deadlock).
     */
    public List<LockedListing> lockInIdOrder(Collection<Long> ids) {
        return jdbcClient
                .sql("""
                        SELECT ilan_id, isletme_id, satis_fiyati, kalan_adet, teslim_baslangic, teslim_bitis, durum
                        FROM PAKET_ILANI
                        WHERE ilan_id IN (:ids)
                        ORDER BY ilan_id
                        FOR UPDATE
                        """)
                .param("ids", ids)
                .query((rs, rowNum) -> new LockedListing(
                        rs.getLong("ilan_id"),
                        rs.getLong("isletme_id"),
                        rs.getBigDecimal("satis_fiyati"),
                        rs.getInt("kalan_adet"),
                        rs.getObject("teslim_baslangic", LocalDateTime.class),
                        rs.getObject("teslim_bitis", LocalDateTime.class),
                        ListingStatus.valueOf(rs.getString("durum"))))
                .list();
    }

    /** @return {@code true} when enough units were left and the stock was decreased */
    public boolean decreaseStock(long id, int quantity) {
        int updated =
                jdbcClient.sql("""
                        UPDATE PAKET_ILANI
                        SET kalan_adet = kalan_adet - :quantity
                        WHERE ilan_id = :id AND kalan_adet >= :quantity
                        """).param("quantity", quantity).param("id", id).update();
        return updated == 1;
    }

    /**
     * Every listing that matches the filter, active or not, newest first.
     *
     * @param from inclusive start of the pickup start range, or {@code null}
     * @param to exclusive end of the pickup start range, or {@code null}
     */
    public List<ListingDetail> findForInspection(
            Long businessId, ListingStatus status, LocalDateTime from, LocalDateTime to, int limit, long offset) {
        return withInspectionFilter(jdbcClient.sql(INSPECTION), businessId, status, from, to)
                .param("limit", limit)
                .param("offset", offset)
                .query(ListingRepository::mapDetail)
                .list();
    }

    public long countForInspection(Long businessId, ListingStatus status, LocalDateTime from, LocalDateTime to) {
        return withInspectionFilter(jdbcClient.sql(INSPECTION_COUNT), businessId, status, from, to)
                .query(Long.class)
                .single();
    }

    /** Changes the sale price for future orders; order items keep the unit price they were bought at. */
    public void updateSalePrice(long id, BigDecimal salePrice) {
        jdbcClient
                .sql("UPDATE PAKET_ILANI SET satis_fiyati = :salePrice WHERE ilan_id = :id")
                .param("salePrice", salePrice)
                .param("id", id)
                .update();
    }

    /** Sets the listing to PASIF; confirmed orders keep their items. */
    public void deactivate(long id) {
        jdbcClient
                .sql("UPDATE PAKET_ILANI SET durum = 'PASIF' WHERE ilan_id = :id")
                .param("id", id)
                .update();
    }

    /** Returns units of a cancelled order; the CHECK constraint keeps kalan_adet within toplam_adet. */
    public void increaseStock(long id, int quantity) {
        jdbcClient
                .sql("UPDATE PAKET_ILANI SET kalan_adet = kalan_adet + :quantity WHERE ilan_id = :id")
                .param("quantity", quantity)
                .param("id", id)
                .update();
    }

    /** Inserts an active listing with all units remaining and returns the generated ilan_id. */
    public long insert(NewListing listing) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("""
                        INSERT INTO PAKET_ILANI (isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat,
                                                 satis_fiyati, toplam_adet, kalan_adet, teslim_baslangic,
                                                 teslim_bitis, durum, olusturulma_zamani)
                        VALUES (:businessId, :title, :description, :allergenInfo, :normalPrice, :salePrice,
                                :totalQuantity, :totalQuantity, :pickupStart, :pickupEnd, 'AKTIF', :createdAt)
                        """)
                .param("businessId", listing.businessId())
                .param("title", listing.title())
                .param("description", listing.description())
                .param("allergenInfo", listing.allergenInfo())
                .param("normalPrice", listing.normalPrice())
                .param("salePrice", listing.salePrice())
                .param("totalQuantity", listing.totalQuantity())
                .param("pickupStart", listing.pickupStart())
                .param("pickupEnd", listing.pickupEnd())
                .param("createdAt", listing.createdAt())
                .update(keyHolder, "ilan_id");
        return Objects.requireNonNull(keyHolder.getKey(), "generated ilan_id").longValue();
    }

    private static JdbcClient.StatementSpec withFilter(
            JdbcClient.StatementSpec spec, ListingFilter filter, LocalDateTime now) {
        return spec.param("now", now)
                .param("city", filter.city())
                .param("district", filter.district())
                .param("type", filter.type() == null ? null : filter.type().name())
                .param("minPrice", filter.minPrice())
                .param("maxPrice", filter.maxPrice());
    }

    private static JdbcClient.StatementSpec withInspectionFilter(
            JdbcClient.StatementSpec spec,
            Long businessId,
            ListingStatus status,
            LocalDateTime from,
            LocalDateTime to) {
        return spec.param("businessId", businessId)
                .param("status", status == null ? null : status.name())
                .param("from", from)
                .param("to", to);
    }

    private static ListingSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ListingSummary(
                rs.getLong("ilan_id"),
                rs.getLong("isletme_id"),
                rs.getString("isletme_adi"),
                BusinessType.valueOf(rs.getString("isletme_turu")),
                rs.getString("sehir"),
                rs.getString("ilce"),
                rs.getString("baslik"),
                rs.getBigDecimal("satis_fiyati"),
                rs.getBigDecimal("normal_fiyat"),
                rs.getInt("kalan_adet"),
                rs.getObject("teslim_baslangic", LocalDateTime.class),
                rs.getObject("teslim_bitis", LocalDateTime.class));
    }

    private static ListingDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        Business business = new Business(
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
        return new ListingDetail(
                rs.getLong("ilan_id"),
                business,
                rs.getString("baslik"),
                rs.getString("aciklama"),
                rs.getString("alerjen_bilgisi"),
                rs.getBigDecimal("normal_fiyat"),
                rs.getBigDecimal("satis_fiyati"),
                rs.getInt("toplam_adet"),
                rs.getInt("kalan_adet"),
                rs.getObject("teslim_baslangic", LocalDateTime.class),
                rs.getObject("teslim_bitis", LocalDateTime.class),
                ListingStatus.valueOf(rs.getString("durum")),
                rs.getObject("olusturulma_zamani", LocalDateTime.class));
    }
}
