package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.edu.etu.endofday.schema.SchemaTestData.BASE_TIME;
import static tr.edu.etu.endofday.schema.SqlErrorAssertions.assertViolatesRule;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.support.IntegrationTest;

/**
 * Proves the cross-table business rules EOD-001 to EOD-008 enforced by the triggers of V2__create_triggers.sql. Each
 * rule is exercised with valid changes that must be stored and invalid ones that must be rejected with SQLSTATE 45000
 * and exactly that rule code. Every test runs as eod_app in its own transaction, which is rolled back afterwards.
 */
@Transactional
class TriggerRulesIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    private SchemaTestData data;

    @BeforeEach
    void setUp() {
        data = new SchemaTestData(jdbcClient);
    }

    // EOD-001: order items belong to the business of the order

    @Test
    void acceptsOrderItemFromListingOfTheOrdersBusiness() {
        long businessId = data.createApprovedBusiness();
        long orderId = data.createOrder(data.createUser(), businessId);

        data.insertOrderItem(data.orderItemRow(orderId, data.createListing(businessId)));

        assertThat(count("SELECT COUNT(*) FROM SIPARIS_KALEMI WHERE siparis_id = :id", orderId))
                .isEqualTo(1);
    }

    @Test
    void rejectsOrderItemFromListingOfAnotherBusiness() {
        long orderId = data.createOrder(data.createUser(), data.createApprovedBusiness());
        long foreignListingId = data.createListing(data.createApprovedBusiness());

        assertViolatesRule("EOD-001", () -> data.insertOrderItem(data.orderItemRow(orderId, foreignListingId)));
    }

    @Test
    void acceptsChangingOrderItemToAnotherListingOfTheSameBusiness() {
        long businessId = data.createApprovedBusiness();
        long orderId = data.createOrder(data.createUser(), businessId);
        long listingId = data.createListing(businessId);
        long otherListingId = data.createListing(businessId);
        data.insertOrderItem(data.orderItemRow(orderId, listingId));

        int updated = changeOrderItemListing(orderId, listingId, otherListingId);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void rejectsChangingOrderItemToListingOfAnotherBusiness() {
        long businessId = data.createApprovedBusiness();
        long orderId = data.createOrder(data.createUser(), businessId);
        long listingId = data.createListing(businessId);
        long foreignListingId = data.createListing(data.createApprovedBusiness());
        data.insertOrderItem(data.orderItemRow(orderId, listingId));

        assertViolatesRule("EOD-001", () -> changeOrderItemListing(orderId, listingId, foreignListingId));
    }

    @Test
    void acceptsChangingBusinessOfOrderWithoutItems() {
        long orderId = data.createOrder(data.createUser(), data.createApprovedBusiness());
        long otherBusinessId = data.createApprovedBusiness();

        int updated = update(
                "UPDATE SIPARIS SET isletme_id = :isletme_id WHERE siparis_id = :id",
                Map.of("isletme_id", otherBusinessId, "id", orderId));

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void rejectsChangingBusinessOfOrderWithItems() {
        long businessId = data.createApprovedBusiness();
        long orderId = data.createOrder(data.createUser(), businessId);
        data.insertOrderItem(data.orderItemRow(orderId, data.createListing(businessId)));
        long otherBusinessId = data.createApprovedBusiness();

        assertViolatesRule(
                "EOD-001",
                () -> update(
                        "UPDATE SIPARIS SET isletme_id = :isletme_id WHERE siparis_id = :id",
                        Map.of("isletme_id", otherBusinessId, "id", orderId)));
    }

    // EOD-002: at most one successful charge per order

    @Test
    void acceptsFirstSuccessfulCharge() {
        long orderId = data.createOrder();

        data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));

        assertThat(successfulCharges(orderId)).isEqualTo(1);
    }

    @Test
    void rejectsSecondSuccessfulCharge() {
        long orderId = data.createOrder();
        data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));

        assertViolatesRule(
                "EOD-002", () -> data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null)));
    }

    @Test
    void acceptsFailedChargeNextToSuccessfulCharge() {
        long orderId = data.createOrder();
        data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));

        data.insertPayment(payment(orderId, "TAHSILAT", "BASARISIZ", "40.00", null));

        assertThat(successfulCharges(orderId)).isEqualTo(1);
    }

    @Test
    void acceptsSuccessfulChargeAfterFailedCharge() {
        long orderId = data.createOrder();
        data.insertPayment(payment(orderId, "TAHSILAT", "BASARISIZ", "40.00", null));

        data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));

        assertThat(successfulCharges(orderId)).isEqualTo(1);
    }

    @Test
    void acceptsTurningFailedChargeIntoSuccessWhenOrderHasNoOtherSuccessfulCharge() {
        long orderId = data.createOrder();
        long failedChargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARISIZ", "40.00", null));

        setPaymentStatus(failedChargeId, "BASARILI");

        assertThat(successfulCharges(orderId)).isEqualTo(1);
    }

    @Test
    void rejectsTurningFailedChargeIntoSuccessWhenOrderIsAlreadyCharged() {
        long orderId = data.createOrder();
        data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        long failedChargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARISIZ", "40.00", null));

        assertViolatesRule("EOD-002", () -> setPaymentStatus(failedChargeId, "BASARILI"));
    }

    // EOD-003: a refund refers to a successful charge of the same order

    @Test
    void acceptsRefundOfSuccessfulChargeOfTheSameOrder() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));

        assertThat(data.insertPayment(payment(orderId, "IADE", "BASARILI", "10.00", chargeId)))
                .isPositive();
    }

    @Test
    void rejectsRefundOfChargeOfAnotherOrder() {
        long otherOrderId = data.createOrder();
        long foreignChargeId = data.insertPayment(payment(otherOrderId, "TAHSILAT", "BASARILI", "40.00", null));
        long orderId = data.createOrder();

        assertViolatesRule(
                "EOD-003", () -> data.insertPayment(payment(orderId, "IADE", "BASARILI", "10.00", foreignChargeId)));
    }

    @Test
    void rejectsRefundOfFailedCharge() {
        long orderId = data.createOrder();
        long failedChargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARISIZ", "40.00", null));

        assertViolatesRule(
                "EOD-003", () -> data.insertPayment(payment(orderId, "IADE", "BASARILI", "10.00", failedChargeId)));
    }

    @Test
    void rejectsRefundOfAnotherRefund() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        long refundId = data.insertPayment(payment(orderId, "IADE", "BASARILI", "10.00", chargeId));

        assertViolatesRule(
                "EOD-003", () -> data.insertPayment(payment(orderId, "IADE", "BASARILI", "10.00", refundId)));
    }

    @Test
    void rejectsMovingRefundToChargeOfAnotherOrder() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        long refundId = data.insertPayment(payment(orderId, "IADE", "BASARILI", "10.00", chargeId));
        long otherOrderId = data.createOrder();
        long foreignChargeId = data.insertPayment(payment(otherOrderId, "TAHSILAT", "BASARILI", "40.00", null));

        assertViolatesRule(
                "EOD-003",
                () -> update(
                        "UPDATE ODEME_ISLEMI SET kaynak_islem_id = :kaynak WHERE islem_id = :id",
                        Map.of("kaynak", foreignChargeId, "id", refundId)));
    }

    // EOD-004: successful refunds never exceed their charge

    @Test
    void acceptsRefundsThatAddUpExactlyToTheCharge() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        data.insertPayment(payment(orderId, "IADE", "BASARILI", "25.00", chargeId));

        data.insertPayment(payment(orderId, "IADE", "BASARILI", "15.00", chargeId));

        assertThat(successfulRefundTotal(chargeId)).isEqualByComparingTo("40.00");
    }

    @Test
    void rejectsRefundsThatExceedTheChargeByOneCent() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        data.insertPayment(payment(orderId, "IADE", "BASARILI", "25.00", chargeId));

        assertViolatesRule(
                "EOD-004", () -> data.insertPayment(payment(orderId, "IADE", "BASARILI", "15.01", chargeId)));
    }

    @Test
    void ignoresFailedRefundsInTheRefundTotal() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        data.insertPayment(payment(orderId, "IADE", "BASARISIZ", "40.00", chargeId));

        data.insertPayment(payment(orderId, "IADE", "BASARILI", "40.00", chargeId));

        assertThat(successfulRefundTotal(chargeId)).isEqualByComparingTo("40.00");
    }

    @Test
    void acceptsFailedRefundEvenWhenItWouldExceedTheCharge() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        data.insertPayment(payment(orderId, "IADE", "BASARILI", "40.00", chargeId));

        assertThat(data.insertPayment(payment(orderId, "IADE", "BASARISIZ", "10.00", chargeId)))
                .isPositive();
    }

    @Test
    void rejectsTurningFailedRefundIntoSuccessWhenTotalWouldExceedTheCharge() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        data.insertPayment(payment(orderId, "IADE", "BASARILI", "30.00", chargeId));
        long failedRefundId = data.insertPayment(payment(orderId, "IADE", "BASARISIZ", "10.01", chargeId));

        assertViolatesRule("EOD-004", () -> setPaymentStatus(failedRefundId, "BASARILI"));
    }

    @Test
    void rejectsRaisingSuccessfulRefundAboveTheCharge() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        long refundId = data.insertPayment(payment(orderId, "IADE", "BASARILI", "40.00", chargeId));

        assertViolatesRule(
                "EOD-004",
                () -> update(
                        "UPDATE ODEME_ISLEMI SET tutar = :tutar WHERE islem_id = :id",
                        Map.of("tutar", new BigDecimal("40.01"), "id", refundId)));
    }

    @Test
    void acceptsUpdatingFullRefundWithoutCountingItTwice() {
        long orderId = data.createOrder();
        long chargeId = data.insertPayment(payment(orderId, "TAHSILAT", "BASARILI", "40.00", null));
        long refundId = data.insertPayment(payment(orderId, "IADE", "BASARILI", "40.00", chargeId));

        int updated = update(
                "UPDATE ODEME_ISLEMI SET islem_zamani = :zaman WHERE islem_id = :id",
                Map.of("zaman", BASE_TIME.plusHours(5), "id", refundId));

        assertThat(updated).isEqualTo(1);
    }

    // EOD-005: order status transitions

    @ParameterizedTest(name = "ONAYLANDI -> {0}")
    @ValueSource(strings = {"TESLIM_EDILDI", "IPTAL_EDILDI", "TESLIM_ALINMADI"})
    void acceptsEveryTransitionFromConfirmedToFinalState(String finalStatus) {
        long orderId = data.createOrder();

        setOrderStatus(orderId, finalStatus);

        assertThat(orderStatus(orderId)).isEqualTo(finalStatus);
    }

    @Test
    void acceptsUpdateThatKeepsConfirmedStatus() {
        long orderId = data.createOrder();

        int updated = update(
                "UPDATE SIPARIS SET durum = 'ONAYLANDI', olusturulma_zamani = :zaman WHERE siparis_id = :id",
                Map.of("zaman", BASE_TIME.minusHours(2), "id", orderId));

        assertThat(updated).isEqualTo(1);
        assertThat(orderStatus(orderId)).isEqualTo("ONAYLANDI");
    }

    @Test
    void rejectsCancellingDeliveredOrder() {
        long orderId = insertOrderWithStatus("TESLIM_EDILDI");

        assertViolatesRule("EOD-005", () -> setOrderStatus(orderId, "IPTAL_EDILDI"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "TESLIM_EDILDI, ONAYLANDI",
        "IPTAL_EDILDI, ONAYLANDI",
        "TESLIM_ALINMADI, ONAYLANDI",
        "TESLIM_ALINMADI, TESLIM_EDILDI",
        "IPTAL_EDILDI, TESLIM_EDILDI"
    })
    void rejectsLeavingFinalState(String fromStatus, String toStatus) {
        long orderId = insertOrderWithStatus(fromStatus);

        assertViolatesRule("EOD-005", () -> setOrderStatus(orderId, toStatus));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"TESLIM_EDILDI", "IPTAL_EDILDI", "TESLIM_ALINMADI"})
    void acceptsInsertingOrderInFinalState(String finalStatus) {
        long orderId = insertOrderWithStatus(finalStatus);

        assertThat(orderStatus(orderId)).isEqualTo(finalStatus);
    }

    // EOD-006: only delivered orders are reviewed

    @Test
    void acceptsReviewOfDeliveredOrder() {
        long orderId = insertOrderWithStatus("TESLIM_EDILDI");

        data.insertReview(data.reviewRow(orderId));

        assertThat(count("SELECT COUNT(*) FROM DEGERLENDIRME WHERE siparis_id = :id", orderId))
                .isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ONAYLANDI", "IPTAL_EDILDI", "TESLIM_ALINMADI"})
    void rejectsReviewOfOrderThatIsNotDelivered(String status) {
        long orderId = insertOrderWithStatus(status);

        assertViolatesRule("EOD-006", () -> data.insertReview(data.reviewRow(orderId)));
    }

    // EOD-007: listings are published only for approved and active businesses

    @Test
    void acceptsActiveListingOfApprovedActiveBusiness() {
        long businessId = data.createApprovedBusiness();

        assertThat(data.insertListing(listing(businessId, "AKTIF"))).isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BEKLEMEDE", "REDDEDILDI"})
    void rejectsActiveListingOfBusinessThatIsNotApproved(String approvalStatus) {
        long businessId = insertBusiness(approvalStatus, true);

        assertViolatesRule("EOD-007", () -> data.insertListing(listing(businessId, "AKTIF")));
    }

    @Test
    void rejectsActiveListingOfInactiveBusiness() {
        long businessId = insertBusiness("ONAYLANDI", false);

        assertViolatesRule("EOD-007", () -> data.insertListing(listing(businessId, "AKTIF")));
    }

    @Test
    void acceptsPassiveListingOfBusinessThatIsNotApproved() {
        long businessId = insertBusiness("BEKLEMEDE", true);

        assertThat(data.insertListing(listing(businessId, "PASIF"))).isPositive();
    }

    @Test
    void acceptsActivatingListingOfApprovedActiveBusiness() {
        long listingId = data.insertListing(listing(data.createApprovedBusiness(), "PASIF"));

        setListingStatus(listingId, "AKTIF");

        assertThat(listingStatus(listingId)).isEqualTo("AKTIF");
    }

    @Test
    void rejectsActivatingListingOfBusinessThatIsNotApproved() {
        long listingId = data.insertListing(listing(insertBusiness("BEKLEMEDE", true), "PASIF"));

        assertViolatesRule("EOD-007", () -> setListingStatus(listingId, "AKTIF"));
    }

    @Test
    void acceptsDecreasingRemainingQuantityWhileBusinessIsInactive() {
        long businessId = data.createApprovedBusiness();
        long listingId = data.insertListing(listing(businessId, "AKTIF"));
        update("UPDATE ISLETME SET aktif_mi = FALSE WHERE isletme_id = :id", Map.of("id", businessId));

        int updated = update(
                "UPDATE PAKET_ILANI SET kalan_adet = kalan_adet - 1 WHERE ilan_id = :id", Map.of("id", listingId));

        assertThat(updated).isEqualTo(1);
        assertThat(count("SELECT kalan_adet FROM PAKET_ILANI WHERE ilan_id = :id", listingId))
                .isEqualTo(9);
    }

    // EOD-008: only the owner of the order's business verifies the pickup

    @Test
    void acceptsPickupVerifiedByBusinessOwner() {
        long orderId = data.createOrder();

        data.insertPickup(verifiedPickup(orderId, data.businessOwnerOfOrder(orderId)));

        assertThat(verifierOf(orderId)).isEqualTo(data.businessOwnerOfOrder(orderId));
    }

    @Test
    void rejectsPickupVerifiedByAnotherUser() {
        long orderId = data.createOrder();
        long customerId = customerOf(orderId);

        assertViolatesRule("EOD-008", () -> data.insertPickup(verifiedPickup(orderId, customerId)));
    }

    @Test
    void acceptsPickupWithoutVerifier() {
        long orderId = data.createOrder();

        data.insertPickup(data.pickupRow(orderId));

        assertThat(count("SELECT COUNT(*) FROM TESLIM_ALMA WHERE siparis_id = :id", orderId))
                .isEqualTo(1);
    }

    @Test
    void acceptsVerifyingPickupLaterByBusinessOwner() {
        long orderId = data.createOrder();
        data.insertPickup(data.pickupRow(orderId));
        long ownerId = data.businessOwnerOfOrder(orderId);

        verifyPickup(orderId, ownerId);

        assertThat(verifierOf(orderId)).isEqualTo(ownerId);
    }

    @Test
    void rejectsVerifyingPickupLaterByAnotherUser() {
        long orderId = data.createOrder();
        data.insertPickup(data.pickupRow(orderId));
        long otherUserId = data.createUser();

        assertViolatesRule("EOD-008", () -> verifyPickup(orderId, otherUserId));
    }

    // Helpers

    private Map<String, Object> payment(
            long orderId, String type, String status, String amount, Long sourceTransactionId) {
        Map<String, Object> row = data.paymentRow(orderId);
        row.put("islem_turu", type);
        row.put("durum", status);
        row.put("tutar", new BigDecimal(amount));
        row.put("kaynak_islem_id", sourceTransactionId);
        return row;
    }

    private Map<String, Object> listing(long businessId, String status) {
        Map<String, Object> row = data.listingRow(businessId);
        row.put("durum", status);
        return row;
    }

    private Map<String, Object> verifiedPickup(long orderId, long verifierId) {
        Map<String, Object> row = data.pickupRow(orderId);
        row.put("teslim_zamani", BASE_TIME.plusMinutes(15));
        row.put("dogrulayan_id", verifierId);
        return row;
    }

    private long insertBusiness(String approvalStatus, boolean active) {
        Map<String, Object> row = data.businessRow(data.createUser());
        row.put("onay_durumu", approvalStatus);
        row.put("aktif_mi", active);
        return data.insertBusiness(row);
    }

    private long insertOrderWithStatus(String status) {
        Map<String, Object> row = data.orderRow(data.createUser(), data.createApprovedBusiness());
        row.put("durum", status);
        row.put("iptal_zamani", "IPTAL_EDILDI".equals(status) ? BASE_TIME.minusMinutes(30) : null);
        return data.insertOrder(row);
    }

    private void setOrderStatus(long orderId, String status) {
        Map<String, Object> params = new HashMap<>();
        params.put("durum", status);
        params.put("iptal_zamani", "IPTAL_EDILDI".equals(status) ? BASE_TIME.minusMinutes(30) : null);
        params.put("id", orderId);
        update("UPDATE SIPARIS SET durum = :durum, iptal_zamani = :iptal_zamani WHERE siparis_id = :id", params);
    }

    private void setPaymentStatus(long transactionId, String status) {
        update(
                "UPDATE ODEME_ISLEMI SET durum = :durum WHERE islem_id = :id",
                Map.of("durum", status, "id", transactionId));
    }

    private void setListingStatus(long listingId, String status) {
        update("UPDATE PAKET_ILANI SET durum = :durum WHERE ilan_id = :id", Map.of("durum", status, "id", listingId));
    }

    private int changeOrderItemListing(long orderId, long fromListingId, long toListingId) {
        return update(
                "UPDATE SIPARIS_KALEMI SET ilan_id = :yeni WHERE siparis_id = :siparis AND ilan_id = :eski",
                Map.of("yeni", toListingId, "siparis", orderId, "eski", fromListingId));
    }

    private void verifyPickup(long orderId, long verifierId) {
        update(
                "UPDATE TESLIM_ALMA SET teslim_zamani = :zaman, dogrulayan_id = :dogrulayan WHERE siparis_id = :id",
                Map.of("zaman", BASE_TIME.plusMinutes(15), "dogrulayan", verifierId, "id", orderId));
    }

    private int update(String sql, Map<String, ?> params) {
        return jdbcClient.sql(sql).params(params).update();
    }

    private long count(String sql, long id) {
        return data.count(sql, Map.of("id", id));
    }

    private long successfulCharges(long orderId) {
        return count(
                "SELECT COUNT(*) FROM ODEME_ISLEMI"
                        + " WHERE siparis_id = :id AND islem_turu = 'TAHSILAT' AND durum = 'BASARILI'",
                orderId);
    }

    private BigDecimal successfulRefundTotal(long chargeId) {
        return jdbcClient
                .sql("SELECT COALESCE(SUM(tutar), 0) FROM ODEME_ISLEMI"
                        + " WHERE kaynak_islem_id = :id AND islem_turu = 'IADE' AND durum = 'BASARILI'")
                .param("id", chargeId)
                .query(BigDecimal.class)
                .single();
    }

    private String orderStatus(long orderId) {
        return jdbcClient
                .sql("SELECT durum FROM SIPARIS WHERE siparis_id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private String listingStatus(long listingId) {
        return jdbcClient
                .sql("SELECT durum FROM PAKET_ILANI WHERE ilan_id = :id")
                .param("id", listingId)
                .query(String.class)
                .single();
    }

    private long customerOf(long orderId) {
        return count("SELECT musteri_id FROM SIPARIS WHERE siparis_id = :id", orderId);
    }

    private long verifierOf(long orderId) {
        return count("SELECT dogrulayan_id FROM TESLIM_ALMA WHERE siparis_id = :id", orderId);
    }
}
