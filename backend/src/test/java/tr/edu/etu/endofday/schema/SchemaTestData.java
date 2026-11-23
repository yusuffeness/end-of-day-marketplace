package tr.edu.etu.endofday.schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Builds valid rows for every schema table so that a test can change a single column and observe how the database
 * reacts.
 *
 * <p>Each {@code ...Row} method returns a mutable map holding one value for every insertable column; each
 * {@code insert...} method writes such a map with a fixed, fully parameterised statement. The {@code create...}
 * methods insert a valid parent row in one call.
 */
final class SchemaTestData {

    /** A fixed point in time; the schema rules compare columns with each other, never with the current time. */
    static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 2, 18, 0);

    /** An identifier that no table ever contains, used to break foreign keys. */
    static final long MISSING_ID = 999_999_999L;

    private static final String INSERT_USER = """
            INSERT INTO KULLANICI (ad, soyad, eposta, telefon, parola_ozeti, aktif_mi, kayit_zamani)
            VALUES (:ad, :soyad, :eposta, :telefon, :parola_ozeti, :aktif_mi, :kayit_zamani)
            """;

    private static final String INSERT_ROLE = "INSERT INTO ROL (rol_adi) VALUES (:rol_adi)";

    private static final String INSERT_USER_ROLE =
            "INSERT INTO KULLANICI_ROL (kullanici_id, rol_id) VALUES (:kullanici_id, :rol_id)";

    private static final String INSERT_BUSINESS = """
            INSERT INTO ISLETME (yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon,
                                 onay_durumu, aktif_mi)
            VALUES (:yetkili_id, :isletme_adi, :isletme_turu, :sehir, :ilce, :adres, :telefon,
                    :onay_durumu, :aktif_mi)
            """;

    private static final String INSERT_LISTING = """
            INSERT INTO PAKET_ILANI (isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat, satis_fiyati,
                                     toplam_adet, kalan_adet, teslim_baslangic, teslim_bitis, durum,
                                     olusturulma_zamani)
            VALUES (:isletme_id, :baslik, :aciklama, :alerjen_bilgisi, :normal_fiyat, :satis_fiyati,
                    :toplam_adet, :kalan_adet, :teslim_baslangic, :teslim_bitis, :durum,
                    :olusturulma_zamani)
            """;

    private static final String INSERT_ORDER = """
            INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
            VALUES (:musteri_id, :isletme_id, :olusturulma_zamani, :durum, :iptal_zamani)
            """;

    private static final String INSERT_ORDER_ITEM = """
            INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
            VALUES (:siparis_id, :ilan_id, :adet, :birim_fiyat)
            """;

    private static final String INSERT_PAYMENT = """
            INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
            VALUES (:siparis_id, :kaynak_islem_id, :istek_no, :islem_turu, :tutar, :durum, :islem_zamani)
            """;

    private static final String INSERT_PICKUP = """
            INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani,
                                     dogrulayan_id)
            VALUES (:siparis_id, :teslim_kodu, :teslim_baslangic, :teslim_bitis, :teslim_zamani,
                    :dogrulayan_id)
            """;

    private static final String INSERT_REVIEW = """
            INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
            VALUES (:siparis_id, :puan, :yorum, :olusturulma_zamani)
            """;

    private final JdbcClient jdbcClient;
    private int sequence;

    SchemaTestData(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // KULLANICI

    Map<String, Object> userRow() {
        int n = next();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ad", "Ayşe");
        row.put("soyad", "Yılmaz");
        row.put("eposta", "kullanici" + n + "@ornek.com");
        row.put("telefon", "0532 000 00 00");
        row.put("parola_ozeti", "$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        row.put("aktif_mi", true);
        row.put("kayit_zamani", BASE_TIME.minusDays(30));
        return row;
    }

    long insertUser(Map<String, Object> row) {
        return insertReturningKey(INSERT_USER, row);
    }

    long createUser() {
        return insertUser(userRow());
    }

    // ROL

    long insertRole(String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rol_adi", name);
        return insertReturningKey(INSERT_ROLE, row);
    }

    long roleId(String name) {
        return jdbcClient
                .sql("SELECT rol_id FROM ROL WHERE rol_adi = :rol_adi")
                .param("rol_adi", name)
                .query(Long.class)
                .single();
    }

    // KULLANICI_ROL

    Map<String, Object> userRoleRow(long userId, long roleId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kullanici_id", userId);
        row.put("rol_id", roleId);
        return row;
    }

    void insertUserRole(Map<String, Object> row) {
        jdbcClient.sql(INSERT_USER_ROLE).params(row).update();
    }

    // ISLETME

    Map<String, Object> businessRow(long ownerId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("yetkili_id", ownerId);
        row.put("isletme_adi", "Mahalle Fırını " + next());
        row.put("isletme_turu", "FIRIN");
        row.put("sehir", "Ankara");
        row.put("ilce", "Çankaya");
        row.put("adres", "Söğütözü Cad. No: 43");
        row.put("telefon", "0312 000 00 00");
        row.put("onay_durumu", "ONAYLANDI");
        row.put("aktif_mi", true);
        return row;
    }

    long insertBusiness(Map<String, Object> row) {
        return insertReturningKey(INSERT_BUSINESS, row);
    }

    /** Creates an approved, active business together with its owner. */
    long createApprovedBusiness() {
        return insertBusiness(businessRow(createUser()));
    }

    // PAKET_ILANI

    Map<String, Object> listingRow(long businessId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("isletme_id", businessId);
        row.put("baslik", "Sürpriz Unlu Mamul Paketi");
        row.put("aciklama", "Gün içinde üretilmiş ekmek ve poğaça.");
        row.put("alerjen_bilgisi", "Gluten, süt, yumurta içerir.");
        row.put("normal_fiyat", new BigDecimal("100.00"));
        row.put("satis_fiyati", new BigDecimal("40.00"));
        row.put("toplam_adet", 10);
        row.put("kalan_adet", 10);
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME.plusHours(2));
        row.put("durum", "AKTIF");
        row.put("olusturulma_zamani", BASE_TIME.minusHours(4));
        return row;
    }

    long insertListing(Map<String, Object> row) {
        return insertReturningKey(INSERT_LISTING, row);
    }

    long createListing(long businessId) {
        return insertListing(listingRow(businessId));
    }

    // SIPARIS

    Map<String, Object> orderRow(long customerId, long businessId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("musteri_id", customerId);
        row.put("isletme_id", businessId);
        row.put("olusturulma_zamani", BASE_TIME.minusHours(1));
        row.put("durum", "ONAYLANDI");
        row.put("iptal_zamani", null);
        return row;
    }

    long insertOrder(Map<String, Object> row) {
        return insertReturningKey(INSERT_ORDER, row);
    }

    long createOrder(long customerId, long businessId) {
        return insertOrder(orderRow(customerId, businessId));
    }

    /** Creates a confirmed order of a new customer at a new approved business. */
    long createOrder() {
        return createOrder(createUser(), createApprovedBusiness());
    }

    // SIPARIS_KALEMI

    Map<String, Object> orderItemRow(long orderId, long listingId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("siparis_id", orderId);
        row.put("ilan_id", listingId);
        row.put("adet", 1);
        row.put("birim_fiyat", new BigDecimal("40.00"));
        return row;
    }

    void insertOrderItem(Map<String, Object> row) {
        jdbcClient.sql(INSERT_ORDER_ITEM).params(row).update();
    }

    // ODEME_ISLEMI

    /** A successful charge for the given order. */
    Map<String, Object> paymentRow(long orderId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("siparis_id", orderId);
        row.put("kaynak_islem_id", null);
        row.put("istek_no", "istek-" + next());
        row.put("islem_turu", "TAHSILAT");
        row.put("tutar", new BigDecimal("40.00"));
        row.put("durum", "BASARILI");
        row.put("islem_zamani", BASE_TIME.minusHours(1));
        return row;
    }

    long insertPayment(Map<String, Object> row) {
        return insertReturningKey(INSERT_PAYMENT, row);
    }

    long createCharge(long orderId) {
        return insertPayment(paymentRow(orderId));
    }

    // TESLIM_ALMA

    /** A pickup that has not been verified yet. */
    Map<String, Object> pickupRow(long orderId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("siparis_id", orderId);
        row.put("teslim_kodu", "%06d".formatted(next()));
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME.plusHours(2));
        row.put("teslim_zamani", null);
        row.put("dogrulayan_id", null);
        return row;
    }

    void insertPickup(Map<String, Object> row) {
        jdbcClient.sql(INSERT_PICKUP).params(row).update();
    }

    // DEGERLENDIRME

    Map<String, Object> reviewRow(long orderId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("siparis_id", orderId);
        row.put("puan", 5);
        row.put("yorum", "Taze ve lezzetliydi.");
        row.put("olusturulma_zamani", BASE_TIME.plusHours(3));
        return row;
    }

    void insertReview(Map<String, Object> row) {
        jdbcClient.sql(INSERT_REVIEW).params(row).update();
    }

    // Helpers

    long count(String countQuery, Map<String, ?> params) {
        return jdbcClient.sql(countQuery).params(params).query(Long.class).single();
    }

    private long insertReturningKey(String sql, Map<String, ?> row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql(sql).params(row).update(keyHolder);
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    private int next() {
        return ++sequence;
    }
}
