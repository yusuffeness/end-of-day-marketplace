package tr.edu.etu.endofday.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** POST /orders/{id}/cancel: refund, stock return and status change in one transaction. */
class OrderCancellationIntegrationTest extends MarketplaceIntegrationTest {

    private Account customer;
    private long bakery;
    private long listing;
    private long orderId;

    @BeforeEach
    void placeOrderOfTwoUnits() throws Exception {
        Account owner = signUpOwner("iptal.sahip");
        customer = signUpCustomer("iptal.musteri");
        bakery = approvedBusiness(owner.id());
        listing = listing(bakery, "120.00", "60.00", 5);
        orderId = placedOrder(customer, bakery, listing, 2);
        clock.setTo(NOW.plusMinutes(30));
    }

    @Test
    void cancellationRefundsTheChargeReturnsTheStockAndCancelsTheOrder() throws Exception {
        String requestId = newRequestId();

        cancelOrder(customer, orderId, requestId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IPTAL_EDILDI"))
                .andExpect(jsonPath("$.cancelledAt").value("2031-03-10T12:30:00"))
                .andExpect(jsonPath("$.totalAmount").value("120.00"))
                .andExpect(jsonPath("$.refundedAmount").value("120.00"))
                .andExpect(jsonPath("$.canCancel").value(false))
                .andExpect(jsonPath("$.canReview").value(false));

        assertThat(orderStatusOf(orderId)).isEqualTo("IPTAL_EDILDI");
        assertThat(remainingOf(listing)).isEqualTo(5);
        List<Map<String, Object>> refunds = refunds();
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().get("durum")).isEqualTo("BASARILI");
        assertThat((BigDecimal) refunds.getFirst().get("tutar")).isEqualByComparingTo("120.00");
        assertThat(refunds.getFirst().get("istek_no")).isEqualTo(requestId);
        assertThat(refunds.getFirst().get("kaynak_islem_id")).isEqualTo(chargeId());
    }

    @Test
    void cancellationAfterThePickupWindowStartedIsRejected() throws Exception {
        clock.setTo(WINDOW_START);

        cancelOrder(customer, orderId, newRequestId(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-019"));
        assertUnchanged();
        getOrder(customer, orderId).andExpect(jsonPath("$.canCancel").value(false));
    }

    @Test
    void canCancelTurnsFalseWhenThePickupWindowStarts() throws Exception {
        clock.setTo(WINDOW_START.minusSeconds(1));
        getOrder(customer, orderId).andExpect(jsonPath("$.canCancel").value(true));

        clock.setTo(WINDOW_START);
        getOrder(customer, orderId).andExpect(jsonPath("$.canCancel").value(false));
    }

    @Test
    void anotherCustomerCannotCancelTheOrder() throws Exception {
        Account other = signUpCustomer("iptal.diger");

        cancelOrder(other, orderId, newRequestId(), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-404"));
        assertUnchanged();
    }

    @Test
    void repeatedRequestCreatesNoSecondRefundOrStockReturn() throws Exception {
        String requestId = newRequestId();
        cancelOrder(customer, orderId, requestId, false).andExpect(status().isOk());

        cancelOrder(customer, orderId, requestId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IPTAL_EDILDI"))
                .andExpect(jsonPath("$.refundedAmount").value("120.00"));
        // A new request id for an order that is already cancelled is a status error, not a second refund.
        cancelOrder(customer, orderId, newRequestId(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-005"));

        assertThat(refunds()).hasSize(1);
        assertThat(remainingOf(listing)).isEqualTo(5);
    }

    @Test
    void declinedRefundIsKeptAsFailedAndTheOrderStaysConfirmed() throws Exception {
        String requestId = newRequestId();

        cancelOrder(customer, orderId, requestId, true)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("EOD-020"));

        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
        assertThat(remainingOf(listing)).isEqualTo(3);
        List<Map<String, Object>> refunds = refunds();
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().get("durum")).isEqualTo("BASARISIZ");
        assertThat(refunds.getFirst().get("kaynak_islem_id")).isEqualTo(chargeId());
        getOrder(customer, orderId)
                .andExpect(jsonPath("$.refundedAmount").value("0.00"))
                .andExpect(jsonPath("$.canCancel").value(true));

        // Repeating the declined request gives the same answer and records nothing new.
        cancelOrder(customer, orderId, requestId, false)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("EOD-020"));
        assertThat(refunds()).hasSize(1);

        // A new attempt succeeds; the failed refund does not count against the charge.
        cancelOrder(customer, orderId, newRequestId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IPTAL_EDILDI"))
                .andExpect(jsonPath("$.refundedAmount").value("120.00"));
        assertThat(refunds()).hasSize(2);
        assertThat(remainingOf(listing)).isEqualTo(5);
    }

    @Test
    void requestIdOfTheOrderChargeCannotBeUsedForTheCancellation() throws Exception {
        String chargeRequestId = db().sql("SELECT istek_no FROM ODEME_ISLEMI WHERE islem_id = :id")
                .param("id", chargeId())
                .query(String.class)
                .single();

        cancelOrder(customer, orderId, chargeRequestId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-018"));
        assertUnchanged();
    }

    @Test
    void deliveredOrderCannotBeCancelled() throws Exception {
        db().sql("UPDATE SIPARIS SET durum = 'TESLIM_EDILDI' WHERE siparis_id = :id")
                .param("id", orderId)
                .update();

        cancelOrder(customer, orderId, newRequestId(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-005"));
        assertThat(refunds()).isEmpty();
    }

    private void assertUnchanged() {
        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
        assertThat(remainingOf(listing)).isEqualTo(3);
        assertThat(refunds()).isEmpty();
    }

    private List<Map<String, Object>> refunds() {
        return db().sql("""
                        SELECT durum, tutar, istek_no, kaynak_islem_id
                        FROM ODEME_ISLEMI
                        WHERE siparis_id = :orderId AND islem_turu = 'IADE'
                        ORDER BY islem_id
                        """).param("orderId", orderId).query().listOfRows();
    }

    private Object chargeId() {
        return db().sql("SELECT islem_id FROM ODEME_ISLEMI WHERE siparis_id = :orderId AND islem_turu = 'TAHSILAT'")
                .param("orderId", orderId)
                .query()
                .singleValue();
    }
}
