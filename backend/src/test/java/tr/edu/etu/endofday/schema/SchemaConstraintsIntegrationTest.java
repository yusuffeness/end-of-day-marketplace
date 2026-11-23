package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tr.edu.etu.endofday.schema.SchemaTestData.BASE_TIME;
import static tr.edu.etu.endofday.schema.SchemaTestData.MISSING_ID;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.OwnerJdbc;

/**
 * Proves every single-table constraint of the schema: each rule is exercised with a valid row that must be stored and
 * with an invalid row that must be rejected for exactly that rule, identified by the MySQL error code and by the name
 * of the violated constraint or column. Every test runs in its own transaction, which is rolled back afterwards,
 * except the two delete tests, which use the eod_owner connection and clean up after themselves.
 */
@Transactional
class SchemaConstraintsIntegrationTest extends IntegrationTest {

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_BAD_NULL_ERROR = 1048;
    private static final int ER_ROW_IS_REFERENCED_2 = 1451;
    private static final int ER_NO_REFERENCED_ROW_2 = 1452;
    private static final int ER_CHECK_CONSTRAINT_VIOLATED = 3819;

    @Autowired
    private JdbcClient jdbcClient;

    private SchemaTestData data;

    @BeforeEach
    void setUp() {
        data = new SchemaTestData(jdbcClient);
    }

    // KULLANICI

    @Test
    void acceptsUserWithoutPhoneNumber() {
        Map<String, Object> row = data.userRow();
        row.put("telefon", null);

        assertThat(data.insertUser(row)).isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ad", "soyad", "eposta", "parola_ozeti", "aktif_mi", "kayit_zamani"})
    void rejectsUserWithoutRequiredValue(String column) {
        Map<String, Object> row = data.userRow();
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertUser(row));
    }

    @Test
    void acceptsUsersWithDifferentEmailAddresses() {
        Map<String, Object> first = data.userRow();
        first.put("eposta", "ali@ornek.com");
        Map<String, Object> second = data.userRow();
        second.put("eposta", "veli@ornek.com");

        data.insertUser(first);

        assertThat(data.insertUser(second)).isPositive();
    }

    @Test
    void rejectsDuplicateEmail() {
        Map<String, Object> first = data.userRow();
        first.put("eposta", "ali@ornek.com");
        Map<String, Object> second = data.userRow();
        second.put("eposta", "ali@ornek.com");
        data.insertUser(first);

        assertRejectedAsDuplicate("uq_kullanici_eposta", () -> data.insertUser(second));
    }

    @Test
    void rejectsEmailThatDiffersOnlyInLetterCase() {
        Map<String, Object> first = data.userRow();
        first.put("eposta", "Ali@ornek.com");
        Map<String, Object> second = data.userRow();
        second.put("eposta", "ali@ornek.com");
        data.insertUser(first);

        assertRejectedAsDuplicate("uq_kullanici_eposta", () -> data.insertUser(second));
    }

    @Test
    void rejectsEmailThatDiffersOnlyInCapitalLetterI() {
        // Under utf8mb4_turkish_ci the capital I would not match the small i.
        Map<String, Object> first = data.userRow();
        first.put("eposta", "ALI@ORNEK.COM");
        Map<String, Object> second = data.userRow();
        second.put("eposta", "ali@ornek.com");
        data.insertUser(first);

        assertRejectedAsDuplicate("uq_kullanici_eposta", () -> data.insertUser(second));
    }

    // ROL

    @Test
    void acceptsNewRoleName() {
        assertThat(data.insertRole("DENETCI")).isPositive();
    }

    @Test
    void rejectsDuplicateRoleName() {
        assertRejectedAsDuplicate("uq_rol_rol_adi", () -> data.insertRole("MUSTERI"));
    }

    @Test
    void storesLowercaseRoleNameSeparatelyFromUppercaseCode() {
        data.insertRole("musteri");

        assertThat(jdbcClient
                        .sql("SELECT rol_adi FROM ROL WHERE rol_adi IN ('MUSTERI', 'musteri')")
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder("MUSTERI", "musteri");
    }

    @Test
    void rejectsRoleWithoutName() {
        assertRejectedAsNull("rol_adi", () -> data.insertRole(null));
    }

    // KULLANICI_ROL

    @Test
    void acceptsSeveralRolesForOneUser() {
        long userId = data.createUser();

        data.insertUserRole(data.userRoleRow(userId, data.roleId("MUSTERI")));
        data.insertUserRole(data.userRoleRow(userId, data.roleId("ISLETME_YETKILISI")));

        assertThat(data.count("SELECT COUNT(*) FROM KULLANICI_ROL WHERE kullanici_id = :id", Map.of("id", userId)))
                .isEqualTo(2);
    }

    @Test
    void rejectsSameRoleTwiceForOneUser() {
        long userId = data.createUser();
        long roleId = data.roleId("MUSTERI");
        data.insertUserRole(data.userRoleRow(userId, roleId));

        assertRejectedAsDuplicate("KULLANICI_ROL.PRIMARY", () -> data.insertUserRole(data.userRoleRow(userId, roleId)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"kullanici_id", "rol_id"})
    void rejectsUserRoleWithoutRequiredValue(String column) {
        Map<String, Object> row = data.userRoleRow(data.createUser(), data.roleId("MUSTERI"));
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertUserRole(row));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"kullanici_id, fk_kullanici_rol_kullanici_id", "rol_id, fk_kullanici_rol_rol_id"})
    void rejectsUserRoleReferencingMissingRow(String column, String foreignKey) {
        Map<String, Object> row = data.userRoleRow(data.createUser(), data.roleId("MUSTERI"));
        row.put(column, MISSING_ID);

        assertRejectedByForeignKey(foreignKey, () -> data.insertUserRole(row));
    }

    // The application never deletes users (report 5.2), so eod_app has no DELETE privilege. The two tests below prove
    // the foreign key delete rules of the schema through the eod_owner connection instead. That connection commits
    // every statement, so these tests run outside the test transaction and remove their own rows.

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingUserAlsoDeletesTheirRoleAssignments() {
        JdbcClient owner = OwnerJdbc.client();
        SchemaTestData ownerData = new SchemaTestData(owner);
        long userId = ownerData.createUser();
        try {
            ownerData.insertUserRole(ownerData.userRoleRow(userId, ownerData.roleId("MUSTERI")));

            int deleted = deleteUser(owner, userId);

            assertThat(deleted).isEqualTo(1);
            assertThat(ownerData.count(
                            "SELECT COUNT(*) FROM KULLANICI_ROL WHERE kullanici_id = :id", Map.of("id", userId)))
                    .isZero();
        } finally {
            deleteUser(owner, userId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsDeletingUserWhoHasOrders() {
        JdbcClient owner = OwnerJdbc.client();
        SchemaTestData ownerData = new SchemaTestData(owner);
        long customerId = ownerData.createUser();
        long businessOwnerId = ownerData.createUser();
        long businessId = ownerData.insertBusiness(ownerData.businessRow(businessOwnerId));
        long orderId = ownerData.createOrder(customerId, businessId);
        try {
            assertRejected(ER_ROW_IS_REFERENCED_2, "`fk_siparis_musteri_id`", () -> deleteUser(owner, customerId));
            assertThat(ownerData.count(
                            "SELECT COUNT(*) FROM KULLANICI WHERE kullanici_id = :id", Map.of("id", customerId)))
                    .isEqualTo(1);
        } finally {
            owner.sql("DELETE FROM SIPARIS WHERE siparis_id = :id")
                    .param("id", orderId)
                    .update();
            owner.sql("DELETE FROM ISLETME WHERE isletme_id = :id")
                    .param("id", businessId)
                    .update();
            deleteUser(owner, customerId);
            deleteUser(owner, businessOwnerId);
        }
    }

    // ISLETME

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"FIRIN", "KAFE", "RESTORAN"})
    void acceptsEveryBusinessType(String type) {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("isletme_turu", type);

        assertThat(data.insertBusiness(row)).isPositive();
    }

    @Test
    void rejectsLowercaseBusinessType() {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("isletme_turu", "kafe");

        assertRejectedByCheck("chk_isletme_isletme_turu", () -> data.insertBusiness(row));
    }

    @Test
    void rejectsUnknownBusinessType() {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("isletme_turu", "MARKET");

        assertRejectedByCheck("chk_isletme_isletme_turu", () -> data.insertBusiness(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BEKLEMEDE", "ONAYLANDI", "REDDEDILDI"})
    void acceptsEveryApprovalStatus(String status) {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("onay_durumu", status);

        assertThat(data.insertBusiness(row)).isPositive();
    }

    @Test
    void rejectsLowercaseApprovalStatus() {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("onay_durumu", "beklemede");

        assertRejectedByCheck("chk_isletme_onay_durumu", () -> data.insertBusiness(row));
    }

    @Test
    void rejectsUnknownApprovalStatus() {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("onay_durumu", "ASKIDA");

        assertRejectedByCheck("chk_isletme_onay_durumu", () -> data.insertBusiness(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "yetkili_id",
                "isletme_adi",
                "isletme_turu",
                "sehir",
                "ilce",
                "adres",
                "telefon",
                "onay_durumu",
                "aktif_mi"
            })
    void rejectsBusinessWithoutRequiredValue(String column) {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertBusiness(row));
    }

    @Test
    void rejectsBusinessReferencingMissingOwner() {
        Map<String, Object> row = data.businessRow(MISSING_ID);

        assertRejectedByForeignKey("fk_isletme_yetkili_id", () -> data.insertBusiness(row));
    }

    // PAKET_ILANI

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"AKTIF", "PASIF"})
    void acceptsEveryListingStatus(String status) {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("durum", status);

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void rejectsLowercaseListingStatus() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("durum", "aktif");

        assertRejectedByCheck("chk_paket_ilani_durum", () -> data.insertListing(row));
    }

    @Test
    void rejectsUnknownListingStatus() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("durum", "SILINDI");

        assertRejectedByCheck("chk_paket_ilani_durum", () -> data.insertListing(row));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    void rejectsBlankDescription(String description) {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("aciklama", description);

        assertRejectedByCheck("chk_paket_ilani_aciklama_not_blank", () -> data.insertListing(row));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    void rejectsBlankAllergenInformation(String allergens) {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("alerjen_bilgisi", allergens);

        assertRejectedByCheck("chk_paket_ilani_alerjen_bilgisi_not_blank", () -> data.insertListing(row));
    }

    @Test
    void acceptsExplicitStatementThatThereAreNoKnownAllergens() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("alerjen_bilgisi", "Bilinen alerjen içermez.");

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void acceptsTotalQuantityOfOne() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("toplam_adet", 1);
        row.put("kalan_adet", 1);

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void rejectsZeroTotalQuantity() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("toplam_adet", 0);
        row.put("kalan_adet", 0);

        assertRejectedByCheck("chk_paket_ilani_toplam_adet_positive", () -> data.insertListing(row));
    }

    @Test
    void acceptsRemainingQuantityEqualToTotal() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("toplam_adet", 10);
        row.put("kalan_adet", 10);

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void acceptsZeroRemainingQuantity() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("kalan_adet", 0);

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void rejectsRemainingQuantityAboveTotal() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("toplam_adet", 10);
        row.put("kalan_adet", 11);

        assertRejectedByCheck("chk_paket_ilani_kalan_adet_within_total", () -> data.insertListing(row));
    }

    @Test
    void acceptsSalePriceOneCentBelowNormalPrice() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("normal_fiyat", new BigDecimal("100.00"));
        row.put("satis_fiyati", new BigDecimal("99.99"));

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void acceptsSalePriceOfOneCent() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("satis_fiyati", new BigDecimal("0.01"));

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void rejectsSalePriceEqualToNormalPrice() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("normal_fiyat", new BigDecimal("100.00"));
        row.put("satis_fiyati", new BigDecimal("100.00"));

        assertRejectedByCheck("chk_paket_ilani_satis_fiyati_discounted", () -> data.insertListing(row));
    }

    @Test
    void rejectsSalePriceAboveNormalPrice() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("normal_fiyat", new BigDecimal("100.00"));
        row.put("satis_fiyati", new BigDecimal("100.01"));

        assertRejectedByCheck("chk_paket_ilani_satis_fiyati_discounted", () -> data.insertListing(row));
    }

    @Test
    void rejectsZeroSalePrice() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("satis_fiyati", new BigDecimal("0.00"));

        assertRejectedByCheck("chk_paket_ilani_satis_fiyati_discounted", () -> data.insertListing(row));
    }

    @Test
    void acceptsListingPickupWindowOfOneMinute() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME.plusMinutes(1));

        assertThat(data.insertListing(row)).isPositive();
    }

    @Test
    void rejectsListingPickupWindowThatEndsWhenItStarts() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME);

        assertRejectedByCheck("chk_paket_ilani_teslim_window", () -> data.insertListing(row));
    }

    @Test
    void rejectsListingPickupWindowThatEndsBeforeItStarts() {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME.minusMinutes(1));

        assertRejectedByCheck("chk_paket_ilani_teslim_window", () -> data.insertListing(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "isletme_id",
                "baslik",
                "aciklama",
                "alerjen_bilgisi",
                "normal_fiyat",
                "satis_fiyati",
                "toplam_adet",
                "kalan_adet",
                "teslim_baslangic",
                "teslim_bitis",
                "durum",
                "olusturulma_zamani"
            })
    void rejectsListingWithoutRequiredValue(String column) {
        Map<String, Object> row = data.listingRow(data.createApprovedBusiness());
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertListing(row));
    }

    @Test
    void rejectsListingReferencingMissingBusiness() {
        Map<String, Object> row = data.listingRow(MISSING_ID);

        assertRejectedByForeignKey("fk_paket_ilani_isletme_id", () -> data.insertListing(row));
    }

    // SIPARIS

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ONAYLANDI", "TESLIM_EDILDI", "TESLIM_ALINMADI"})
    void acceptsOrderThatIsNotCancelledWithoutCancellationTime(String status) {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", status);
        row.put("iptal_zamani", null);

        assertThat(data.insertOrder(row)).isPositive();
    }

    @Test
    void acceptsCancelledOrderWithCancellationTime() {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", "IPTAL_EDILDI");
        row.put("iptal_zamani", BASE_TIME.minusMinutes(30));

        assertThat(data.insertOrder(row)).isPositive();
    }

    @Test
    void rejectsLowercaseOrderStatus() {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", "onaylandi");

        assertRejectedByCheck("chk_siparis_durum", () -> data.insertOrder(row));
    }

    @Test
    void rejectsUnknownOrderStatus() {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", "BEKLEMEDE");

        assertRejectedByCheck("chk_siparis_durum", () -> data.insertOrder(row));
    }

    @Test
    void rejectsCancelledOrderWithoutCancellationTime() {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", "IPTAL_EDILDI");
        row.put("iptal_zamani", null);

        assertRejectedByCheck("chk_siparis_iptal_zamani", () -> data.insertOrder(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ONAYLANDI", "TESLIM_EDILDI", "TESLIM_ALINMADI"})
    void rejectsCancellationTimeOnOrderThatIsNotCancelled(String status) {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", status);
        row.put("iptal_zamani", BASE_TIME.minusMinutes(30));

        assertRejectedByCheck("chk_siparis_iptal_zamani", () -> data.insertOrder(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"musteri_id", "isletme_id", "olusturulma_zamani", "durum"})
    void rejectsOrderWithoutRequiredValue(String column) {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertOrder(row));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"musteri_id, fk_siparis_musteri_id", "isletme_id, fk_siparis_isletme_id"})
    void rejectsOrderReferencingMissingRow(String column, String foreignKey) {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put(column, MISSING_ID);

        assertRejectedByForeignKey(foreignKey, () -> data.insertOrder(row));
    }

    // SIPARIS_KALEMI

    @Test
    void acceptsOrderItemWithQuantityOfOneAndUnitPriceOfOneCent() {
        Map<String, Object> row = newOrderItemRow();
        row.put("adet", 1);
        row.put("birim_fiyat", new BigDecimal("0.01"));

        data.insertOrderItem(row);

        assertThat(data.count(
                        "SELECT COUNT(*) FROM SIPARIS_KALEMI WHERE siparis_id = :id",
                        Map.of("id", row.get("siparis_id"))))
                .isEqualTo(1);
    }

    @Test
    void rejectsOrderItemWithZeroQuantity() {
        Map<String, Object> row = newOrderItemRow();
        row.put("adet", 0);

        assertRejectedByCheck("chk_siparis_kalemi_adet_positive", () -> data.insertOrderItem(row));
    }

    @Test
    void rejectsOrderItemWithZeroUnitPrice() {
        Map<String, Object> row = newOrderItemRow();
        row.put("birim_fiyat", new BigDecimal("0.00"));

        assertRejectedByCheck("chk_siparis_kalemi_birim_fiyat_positive", () -> data.insertOrderItem(row));
    }

    @Test
    void rejectsSameListingTwiceInOneOrder() {
        Map<String, Object> row = newOrderItemRow();
        data.insertOrderItem(row);

        assertRejectedAsDuplicate("SIPARIS_KALEMI.PRIMARY", () -> data.insertOrderItem(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"siparis_id", "ilan_id", "adet", "birim_fiyat"})
    void rejectsOrderItemWithoutRequiredValue(String column) {
        Map<String, Object> row = newOrderItemRow();
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertOrderItem(row));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"siparis_id, fk_siparis_kalemi_siparis_id", "ilan_id, fk_siparis_kalemi_ilan_id"})
    void rejectsOrderItemReferencingMissingRow(String column, String foreignKey) {
        Map<String, Object> row = newOrderItemRow();
        row.put(column, MISSING_ID);

        assertRejectedByForeignKey(foreignKey, () -> data.insertOrderItem(row));
    }

    // ODEME_ISLEMI

    @Test
    void acceptsChargeWithoutSourceTransaction() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("islem_turu", "TAHSILAT");
        row.put("kaynak_islem_id", null);

        assertThat(data.insertPayment(row)).isPositive();
    }

    @Test
    void acceptsRefundThatReferencesItsCharge() {
        long orderId = data.createOrder();
        long chargeId = data.createCharge(orderId);
        Map<String, Object> row = data.paymentRow(orderId);
        row.put("islem_turu", "IADE");
        row.put("kaynak_islem_id", chargeId);

        assertThat(data.insertPayment(row)).isPositive();
    }

    @Test
    void rejectsChargeThatReferencesSourceTransaction() {
        long orderId = data.createOrder();
        long chargeId = data.createCharge(orderId);
        Map<String, Object> row = data.paymentRow(orderId);
        row.put("islem_turu", "TAHSILAT");
        row.put("kaynak_islem_id", chargeId);

        assertRejectedByCheck("chk_odeme_islemi_kaynak_islem", () -> data.insertPayment(row));
    }

    @Test
    void rejectsRefundWithoutSourceTransaction() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("islem_turu", "IADE");
        row.put("kaynak_islem_id", null);

        assertRejectedByCheck("chk_odeme_islemi_kaynak_islem", () -> data.insertPayment(row));
    }

    @Test
    void rejectsLowercaseTransactionType() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("islem_turu", "tahsilat");

        assertRejectedByCheck("chk_odeme_islemi_islem_turu", () -> data.insertPayment(row));
    }

    @Test
    void rejectsUnknownTransactionType() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("islem_turu", "DUZELTME");

        assertRejectedByCheck("chk_odeme_islemi_islem_turu", () -> data.insertPayment(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BASARILI", "BASARISIZ"})
    void acceptsEveryPaymentResult(String status) {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("durum", status);

        assertThat(data.insertPayment(row)).isPositive();
    }

    @Test
    void rejectsLowercasePaymentResult() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("durum", "basarili");

        assertRejectedByCheck("chk_odeme_islemi_durum", () -> data.insertPayment(row));
    }

    @Test
    void rejectsUnknownPaymentResult() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("durum", "BEKLIYOR");

        assertRejectedByCheck("chk_odeme_islemi_durum", () -> data.insertPayment(row));
    }

    @Test
    void acceptsPaymentAmountOfOneCent() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("tutar", new BigDecimal("0.01"));

        assertThat(data.insertPayment(row)).isPositive();
    }

    @Test
    void rejectsZeroPaymentAmount() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("tutar", new BigDecimal("0.00"));

        assertRejectedByCheck("chk_odeme_islemi_tutar_positive", () -> data.insertPayment(row));
    }

    @Test
    void rejectsDuplicateRequestNumber() {
        long orderId = data.createOrder();
        Map<String, Object> first = data.paymentRow(orderId);
        first.put("istek_no", "istek-tekrar");
        Map<String, Object> second = data.paymentRow(orderId);
        second.put("istek_no", "istek-tekrar");
        data.insertPayment(first);

        assertRejectedAsDuplicate("uq_odeme_islemi_istek_no", () -> data.insertPayment(second));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"siparis_id", "istek_no", "islem_turu", "tutar", "durum", "islem_zamani"})
    void rejectsPaymentWithoutRequiredValue(String column) {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertPayment(row));
    }

    @Test
    void rejectsPaymentReferencingMissingOrder() {
        Map<String, Object> row = data.paymentRow(MISSING_ID);

        assertRejectedByForeignKey("fk_odeme_islemi_siparis_id", () -> data.insertPayment(row));
    }

    @Test
    void rejectsRefundReferencingMissingSourceTransaction() {
        Map<String, Object> row = data.paymentRow(data.createOrder());
        row.put("islem_turu", "IADE");
        row.put("kaynak_islem_id", MISSING_ID);

        assertRejectedByForeignKey("fk_odeme_islemi_kaynak_islem_id", () -> data.insertPayment(row));
    }

    // TESLIM_ALMA

    @Test
    void acceptsPickupAwaitingVerification() {
        long orderId = data.createOrder();
        Map<String, Object> row = data.pickupRow(orderId);
        row.put("teslim_zamani", null);
        row.put("dogrulayan_id", null);

        data.insertPickup(row);

        assertThat(pickupCount(orderId)).isEqualTo(1);
    }

    @Test
    void acceptsVerifiedPickupWithTimeAndVerifier() {
        long orderId = data.createOrder();
        Map<String, Object> row = data.pickupRow(orderId);
        row.put("teslim_zamani", BASE_TIME.plusMinutes(15));
        row.put("dogrulayan_id", data.createUser());

        data.insertPickup(row);

        assertThat(pickupCount(orderId)).isEqualTo(1);
    }

    @Test
    void rejectsPickupTimeWithoutVerifier() {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put("teslim_zamani", BASE_TIME.plusMinutes(15));
        row.put("dogrulayan_id", null);

        assertRejectedByCheck("chk_teslim_alma_verification", () -> data.insertPickup(row));
    }

    @Test
    void rejectsVerifierWithoutPickupTime() {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put("teslim_zamani", null);
        row.put("dogrulayan_id", data.createUser());

        assertRejectedByCheck("chk_teslim_alma_verification", () -> data.insertPickup(row));
    }

    @Test
    void acceptsPickupCodeOfSixDigitsWithLeadingZeros() {
        long orderId = data.createOrder();
        Map<String, Object> row = data.pickupRow(orderId);
        row.put("teslim_kodu", "000123");

        data.insertPickup(row);

        assertThat(pickupCount(orderId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"12345", "12a456", "ABCDEF", "12 456"})
    void rejectsPickupCodeThatIsNotSixDigits(String code) {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put("teslim_kodu", code);

        assertRejectedByCheck("chk_teslim_alma_teslim_kodu_format", () -> data.insertPickup(row));
    }

    @Test
    void rejectsDuplicatePickupCode() {
        Map<String, Object> first = data.pickupRow(data.createOrder());
        first.put("teslim_kodu", "482915");
        Map<String, Object> second = data.pickupRow(data.createOrder());
        second.put("teslim_kodu", "482915");
        data.insertPickup(first);

        assertRejectedAsDuplicate("uq_teslim_alma_teslim_kodu", () -> data.insertPickup(second));
    }

    @Test
    void rejectsSecondPickupForSameOrder() {
        long orderId = data.createOrder();
        data.insertPickup(data.pickupRow(orderId));

        assertRejectedAsDuplicate("TESLIM_ALMA.PRIMARY", () -> data.insertPickup(data.pickupRow(orderId)));
    }

    @Test
    void acceptsPickupWindowOfOneMinute() {
        long orderId = data.createOrder();
        Map<String, Object> row = data.pickupRow(orderId);
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME.plusMinutes(1));

        data.insertPickup(row);

        assertThat(pickupCount(orderId)).isEqualTo(1);
    }

    @Test
    void rejectsPickupWindowThatEndsWhenItStarts() {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME);

        assertRejectedByCheck("chk_teslim_alma_teslim_window", () -> data.insertPickup(row));
    }

    @Test
    void rejectsPickupWindowThatEndsBeforeItStarts() {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put("teslim_baslangic", BASE_TIME);
        row.put("teslim_bitis", BASE_TIME.minusMinutes(1));

        assertRejectedByCheck("chk_teslim_alma_teslim_window", () -> data.insertPickup(row));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"siparis_id", "teslim_kodu", "teslim_baslangic", "teslim_bitis"})
    void rejectsPickupWithoutRequiredValue(String column) {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertPickup(row));
    }

    @Test
    void rejectsPickupReferencingMissingOrder() {
        Map<String, Object> row = data.pickupRow(MISSING_ID);

        assertRejectedByForeignKey("fk_teslim_alma_siparis_id", () -> data.insertPickup(row));
    }

    @Test
    void rejectsPickupVerifiedByMissingUser() {
        Map<String, Object> row = data.pickupRow(data.createOrder());
        row.put("teslim_zamani", BASE_TIME.plusMinutes(15));
        row.put("dogrulayan_id", MISSING_ID);

        assertRejectedByForeignKey("fk_teslim_alma_dogrulayan_id", () -> data.insertPickup(row));
    }

    // DEGERLENDIRME

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {1, 5})
    void acceptsRatingsAtBothEndsOfTheScale(int rating) {
        long orderId = data.createOrder();
        Map<String, Object> row = data.reviewRow(orderId);
        row.put("puan", rating);

        data.insertReview(row);

        assertThat(reviewCount(orderId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {0, 6})
    void rejectsRatingOutsideOneToFive(int rating) {
        Map<String, Object> row = data.reviewRow(data.createOrder());
        row.put("puan", rating);

        assertRejectedByCheck("chk_degerlendirme_puan_range", () -> data.insertReview(row));
    }

    @Test
    void acceptsReviewWithoutComment() {
        long orderId = data.createOrder();
        Map<String, Object> row = data.reviewRow(orderId);
        row.put("yorum", null);

        data.insertReview(row);

        assertThat(reviewCount(orderId)).isEqualTo(1);
    }

    @Test
    void rejectsSecondReviewForSameOrder() {
        long orderId = data.createOrder();
        data.insertReview(data.reviewRow(orderId));

        assertRejectedAsDuplicate("DEGERLENDIRME.PRIMARY", () -> data.insertReview(data.reviewRow(orderId)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"siparis_id", "puan", "olusturulma_zamani"})
    void rejectsReviewWithoutRequiredValue(String column) {
        Map<String, Object> row = data.reviewRow(data.createOrder());
        row.put(column, null);

        assertRejectedAsNull(column, () -> data.insertReview(row));
    }

    @Test
    void rejectsReviewReferencingMissingOrder() {
        Map<String, Object> row = data.reviewRow(MISSING_ID);

        assertRejectedByForeignKey("fk_degerlendirme_siparis_id", () -> data.insertReview(row));
    }

    // Helpers

    private Map<String, Object> newOrderItemRow() {
        long customerId = data.createUser();
        long businessId = data.createApprovedBusiness();
        long listingId = data.createListing(businessId);
        long orderId = data.createOrder(customerId, businessId);
        return data.orderItemRow(orderId, listingId);
    }

    private static int deleteUser(JdbcClient client, long userId) {
        return client.sql("DELETE FROM KULLANICI WHERE kullanici_id = :id")
                .param("id", userId)
                .update();
    }

    private long pickupCount(long orderId) {
        return data.count("SELECT COUNT(*) FROM TESLIM_ALMA WHERE siparis_id = :id", Map.of("id", orderId));
    }

    private long reviewCount(long orderId) {
        return data.count("SELECT COUNT(*) FROM DEGERLENDIRME WHERE siparis_id = :id", Map.of("id", orderId));
    }

    private static void assertRejectedByCheck(String constraint, ThrowingCallable insert) {
        assertRejected(ER_CHECK_CONSTRAINT_VIOLATED, "'" + constraint + "'", insert);
    }

    private static void assertRejectedAsDuplicate(String key, ThrowingCallable insert) {
        assertRejected(ER_DUP_ENTRY, key, insert);
    }

    private static void assertRejectedByForeignKey(String foreignKey, ThrowingCallable insert) {
        assertRejected(ER_NO_REFERENCED_ROW_2, "`" + foreignKey + "`", insert);
    }

    private static void assertRejectedAsNull(String column, ThrowingCallable insert) {
        assertRejected(ER_BAD_NULL_ERROR, "'" + column + "'", insert);
    }

    /**
     * Asserts that the statement fails with the given MySQL error code and that the error message names the violated
     * constraint, key or column.
     */
    private static void assertRejected(int errorCode, String messageFragment, ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(((DataAccessException) ex).getMostSpecificCause())
                        .isInstanceOfSatisfying(SQLException.class, sqlException -> {
                            assertThat(sqlException.getErrorCode()).isEqualTo(errorCode);
                            assertThat(sqlException.getMessage()).contains(messageFragment);
                        }));
    }
}
