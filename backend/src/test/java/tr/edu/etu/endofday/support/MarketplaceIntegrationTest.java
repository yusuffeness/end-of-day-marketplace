package tr.edu.etu.endofday.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base class for the marketplace API tests: a hand-set clock, signed-in customers and business owners on the test
 * domain, and businesses and listings inserted through the owner connection. All rows the tests create are removed
 * after each test.
 *
 * <p>The clock starts at {@link #NOW}, far in the future, so that the not-picked-up event of the database (which uses
 * the real NOW()) never touches the test orders. Listings get the pickup window {@link #WINDOW_START} to
 * {@link #WINDOW_END} on the same day unless a test says otherwise.
 */
@Import(TestClockConfig.class)
public abstract class MarketplaceIntegrationTest extends IntegrationTest {

    protected static final LocalDateTime NOW = LocalDateTime.of(2031, 3, 10, 12, 0);
    protected static final LocalDateTime WINDOW_START = LocalDateTime.of(2031, 3, 10, 19, 0);
    protected static final LocalDateTime WINDOW_END = LocalDateTime.of(2031, 3, 10, 20, 0);

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    /** A city name of its own per test, so that searches see only the test's listings. */
    protected String city;

    @BeforeEach
    void resetMarketplace() {
        clock.setTo(NOW);
        city = "Test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void deleteMarketplaceData() {
        deleteTestData();
    }

    /** A signed-in account on the test domain. */
    public record Account(long id, String email, ApiClient client) {}

    protected Account signUpCustomer(String localPart) throws Exception {
        String email = TestAccounts.uniqueEmail(localPart);
        ApiClient client = new ApiClient(mockMvc);
        client.fetchCsrfToken();
        client.register("Test", "Müşteri", email, TestAccounts.PASSWORD).andExpect(status().isCreated());
        client.login(email, TestAccounts.PASSWORD).andExpect(status().isOk());
        return new Account(TestAccounts.idOf(email), email, client);
    }

    /** A customer who also holds the ISLETME_YETKILISI role. */
    protected Account signUpOwner(String localPart) throws Exception {
        Account account = signUpCustomer(localPart);
        grantRole(account.id(), "ISLETME_YETKILISI");
        return account;
    }

    /** A customer who also holds the YONETICI role. */
    protected Account signUpAdmin(String localPart) throws Exception {
        Account account = signUpCustomer(localPart);
        grantRole(account.id(), "YONETICI");
        return account;
    }

    /** A signed-in customer with the given names, for tests that look at how names are shown. */
    protected Account signUpCustomer(String localPart, String firstName, String lastName) throws Exception {
        String email = TestAccounts.uniqueEmail(localPart);
        ApiClient client = new ApiClient(mockMvc);
        client.fetchCsrfToken();
        client.register(firstName, lastName, email, TestAccounts.PASSWORD).andExpect(status().isCreated());
        client.login(email, TestAccounts.PASSWORD).andExpect(status().isOk());
        return new Account(TestAccounts.idOf(email), email, client);
    }

    protected static void grantRole(long userId, String roleName) {
        db().sql("""
                        INSERT INTO KULLANICI_ROL (kullanici_id, rol_id)
                        SELECT :userId, rol_id FROM ROL WHERE rol_adi = :roleName
                        """).param("userId", userId).param("roleName", roleName).update();
    }

    protected static boolean hasRole(long userId, String roleName) {
        return db().sql("""
                        SELECT EXISTS (SELECT 1 FROM KULLANICI_ROL kr JOIN ROL r ON r.rol_id = kr.rol_id
                                       WHERE kr.kullanici_id = :userId AND r.rol_adi = :roleName)
                        """)
                .param("userId", userId)
                .param("roleName", roleName)
                .query(Boolean.class)
                .single();
    }

    protected static String approvalOf(long businessId) {
        return db().sql("SELECT onay_durumu FROM ISLETME WHERE isletme_id = :id")
                .param("id", businessId)
                .query(String.class)
                .single();
    }

    /** An approved and active bakery in the test's city. */
    protected long approvedBusiness(long ownerId) {
        return business(ownerId, "Çankaya", "FIRIN", "ONAYLANDI", true);
    }

    protected long business(long ownerId, String district, String type, String approval, boolean active) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        db().sql("""
                        INSERT INTO ISLETME (yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon,
                                             onay_durumu, aktif_mi)
                        VALUES (:ownerId, :name, :type, :city, :district, 'Test Cad. No: 1', '0312 555 00 00',
                                :approval, :active)
                        """)
                .param("ownerId", ownerId)
                .param("name", "Test İşletmesi " + UUID.randomUUID().toString().substring(0, 8))
                .param("type", type)
                .param("city", city)
                .param("district", district)
                .param("approval", approval)
                .param("active", active)
                .update(keyHolder, "isletme_id");
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    /** An active listing in the default window with all units remaining. */
    protected long listing(long businessId, String normalPrice, String salePrice, int quantity) {
        return listing(businessId, normalPrice, salePrice, quantity, quantity, WINDOW_START, WINDOW_END, "AKTIF");
    }

    protected long listing(
            long businessId,
            String normalPrice,
            String salePrice,
            int total,
            int remaining,
            LocalDateTime start,
            LocalDateTime end,
            String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        db().sql("""
                        INSERT INTO PAKET_ILANI (isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat,
                                                 satis_fiyati, toplam_adet, kalan_adet, teslim_baslangic,
                                                 teslim_bitis, durum, olusturulma_zamani)
                        VALUES (:businessId, :title, 'Günün ürünlerinden karışık paket.', 'Gluten içerir.',
                                :normalPrice, :salePrice, :total, :remaining, :start, :end, :status, :createdAt)
                        """)
                .param("businessId", businessId)
                .param("title", "Test Paketi " + UUID.randomUUID().toString().substring(0, 8))
                .param("normalPrice", new BigDecimal(normalPrice))
                .param("salePrice", new BigDecimal(salePrice))
                .param("total", total)
                .param("remaining", remaining)
                .param("start", start)
                .param("end", end)
                .param("status", status)
                .param("createdAt", NOW.minusHours(1))
                .update(keyHolder, "ilan_id");
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    protected ResultActions placeOrder(Account customer, long businessId, String requestId, String itemsJson)
            throws Exception {
        return customer.client().postJson("/api/v1/orders", """
                {"businessId": %d, "items": %s, "requestId": "%s"}
                """.formatted(businessId, itemsJson, requestId));
    }

    protected ResultActions placeOrder(Account customer, long businessId, long listingId, int quantity)
            throws Exception {
        return placeOrder(
                customer,
                businessId,
                newRequestId(),
                "[{\"listingId\": %d, \"quantity\": %d}]".formatted(listingId, quantity));
    }

    /** Places an order that must succeed and returns its id. */
    protected long placedOrder(Account customer, long businessId, long listingId, int quantity) throws Exception {
        return json(placeOrder(customer, businessId, listingId, quantity).andExpect(status().isCreated()))
                .get("id")
                .asLong();
    }

    protected ResultActions cancelOrder(Account customer, long orderId, String requestId, boolean refundFails)
            throws Exception {
        return customer.client()
                .postJson("/api/v1/orders/" + orderId + "/cancel", """
                {"requestId": "%s", "simulateRefundFailure": %s}
                """.formatted(requestId, refundFails));
    }

    protected ResultActions verifyPickup(Account owner, long businessId, String code) throws Exception {
        return owner.client().postJson("/api/v1/businesses/" + businessId + "/pickups/verify", """
                {"code": "%s"}
                """.formatted(code));
    }

    protected ResultActions getOrder(Account customer, long orderId) throws Exception {
        return customer.client().perform(get("/api/v1/orders/" + orderId));
    }

    protected ResultActions postWithoutBody(Account account, String path) throws Exception {
        return account.client().perform(post(path).contentType(MediaType.APPLICATION_JSON));
    }

    protected String pickupCodeOf(long orderId) {
        return db().sql("SELECT teslim_kodu FROM TESLIM_ALMA WHERE siparis_id = :orderId")
                .param("orderId", orderId)
                .query(String.class)
                .single();
    }

    protected int remainingOf(long listingId) {
        return db().sql("SELECT kalan_adet FROM PAKET_ILANI WHERE ilan_id = :id")
                .param("id", listingId)
                .query(Integer.class)
                .single();
    }

    protected String orderStatusOf(long orderId) {
        return db().sql("SELECT durum FROM SIPARIS WHERE siparis_id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    protected long countOrdersOfBusiness(long businessId) {
        return db().sql("SELECT COUNT(*) FROM SIPARIS WHERE isletme_id = :businessId")
                .param("businessId", businessId)
                .query(Long.class)
                .single();
    }

    protected static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    protected static JsonNode json(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** The owner connection: commits immediately and sees every table. */
    protected static JdbcClient db() {
        return OwnerJdbc.client();
    }

    /** Removes every row that belongs to a business or a customer on the test domain, children first. */
    protected static void deleteTestData() {
        // Fixed SQL fragments only; the domain is passed as the named parameter :pattern.
        String testUsers = "SELECT kullanici_id FROM KULLANICI WHERE eposta LIKE :pattern";
        String testOrders = "SELECT siparis_id FROM SIPARIS WHERE musteri_id IN (" + testUsers
                + ") OR isletme_id IN (SELECT isletme_id FROM ISLETME WHERE yetkili_id IN (" + testUsers + "))";
        String pattern = "%@" + TestAccounts.DOMAIN;
        JdbcClient db = db();
        db.sql("DELETE FROM DEGERLENDIRME WHERE siparis_id IN (" + testOrders + ")")
                .param("pattern", pattern)
                .update();
        db.sql("DELETE FROM TESLIM_ALMA WHERE siparis_id IN (" + testOrders + ")")
                .param("pattern", pattern)
                .update();
        db.sql("DELETE FROM ODEME_ISLEMI WHERE kaynak_islem_id IS NOT NULL AND siparis_id IN (" + testOrders + ")")
                .param("pattern", pattern)
                .update();
        db.sql("DELETE FROM ODEME_ISLEMI WHERE siparis_id IN (" + testOrders + ")")
                .param("pattern", pattern)
                .update();
        db.sql("DELETE FROM SIPARIS_KALEMI WHERE siparis_id IN (" + testOrders + ")")
                .param("pattern", pattern)
                .update();
        // MySQL cannot delete from a table that its own subquery reads, so the ids go through a derived table.
        db.sql("DELETE FROM SIPARIS WHERE siparis_id IN (SELECT siparis_id FROM (" + testOrders + ") AS t)")
                .param("pattern", pattern)
                .update();
        db.sql("DELETE FROM PAKET_ILANI WHERE isletme_id IN (SELECT isletme_id FROM ISLETME WHERE yetkili_id IN ("
                        + testUsers + "))")
                .param("pattern", pattern)
                .update();
        db.sql("DELETE FROM ISLETME WHERE yetkili_id IN (" + testUsers + ")")
                .param("pattern", pattern)
                .update();
        TestAccounts.deleteAll();
    }
}
