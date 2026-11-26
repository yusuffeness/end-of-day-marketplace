package tr.edu.etu.endofday.pickup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** POST /businesses/{id}/pickups/verify: code, ownership, window and single use. */
class PickupIntegrationTest extends MarketplaceIntegrationTest {

    private static final LocalDateTime DURING_WINDOW = WINDOW_START.plusMinutes(15);

    private Account owner;
    private Account customer;
    private long bakery;
    private long orderId;
    private String code;

    @BeforeEach
    void placeOrder() throws Exception {
        owner = signUpOwner("teslim.sahip");
        customer = signUpCustomer("teslim.musteri");
        bakery = approvedBusiness(owner.id());
        orderId = placedOrder(customer, bakery, listing(bakery, "120.00", "60.00", 5), 2);
        code = pickupCodeOf(orderId);
    }

    @Test
    void correctCodeInsideTheWindowDeliversTheOrder() throws Exception {
        clock.setTo(DURING_WINDOW);

        verifyPickup(owner, bakery, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("TESLIM_EDILDI"))
                .andExpect(jsonPath("$.pickup.pickedUpAt").value("2031-03-10T19:15:00"));

        Map<String, Object> pickup = db().sql(
                        "SELECT teslim_zamani, dogrulayan_id FROM TESLIM_ALMA WHERE siparis_id = :id")
                .param("id", orderId)
                .query()
                .singleRow();
        assertThat(pickup.get("teslim_zamani")).isEqualTo(DURING_WINDOW);
        assertThat(((Number) pickup.get("dogrulayan_id")).longValue()).isEqualTo(owner.id());
        assertThat(orderStatusOf(orderId)).isEqualTo("TESLIM_EDILDI");
        getOrder(customer, orderId).andExpect(jsonPath("$.canReview").value(true));
    }

    @Test
    void bothEndsOfTheWindowCount() throws Exception {
        clock.setTo(WINDOW_END);
        verifyPickup(owner, bakery, code).andExpect(status().isOk());
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        clock.setTo(DURING_WINDOW);
        String wrong = code.equals("000000") ? "000001" : "000000";

        verifyPickup(owner, bakery, wrong)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-021"));
        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
    }

    @Test
    void ownerOfAnotherBusinessCannotDeliver() throws Exception {
        clock.setTo(DURING_WINDOW);
        Account otherOwner = signUpOwner("teslim.baska");
        long otherBusiness = approvedBusiness(otherOwner.id());

        verifyPickup(otherOwner, bakery, code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-008"));
        // Through their own business the code of another business is unknown.
        verifyPickup(otherOwner, otherBusiness, code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-021"));
        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
    }

    @Test
    void customerWithoutTheOwnerRoleCannotDeliver() throws Exception {
        clock.setTo(DURING_WINDOW);

        verifyPickup(customer, bakery, code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    @Test
    void deliveryOutsideTheWindowIsRejected() throws Exception {
        clock.setTo(WINDOW_START.minusSeconds(1));
        verifyPickup(owner, bakery, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-022"));

        clock.setTo(WINDOW_END.plusSeconds(1));
        verifyPickup(owner, bakery, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-022"));
        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
    }

    @Test
    void codeCanBeUsedOnlyOnce() throws Exception {
        clock.setTo(DURING_WINDOW);
        verifyPickup(owner, bakery, code).andExpect(status().isOk());

        clock.setTo(DURING_WINDOW.plusMinutes(5));
        verifyPickup(owner, bakery, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-023"));
        assertThat(db().sql("SELECT teslim_zamani FROM TESLIM_ALMA WHERE siparis_id = :id")
                        .param("id", orderId)
                        .query(LocalDateTime.class)
                        .single())
                .isEqualTo(DURING_WINDOW);
    }

    @Test
    void codeOfACancelledOrderIsNotAccepted() throws Exception {
        cancelOrder(customer, orderId, newRequestId(), false).andExpect(status().isOk());
        clock.setTo(DURING_WINDOW);

        verifyPickup(owner, bakery, code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-021"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12a456", ""})
    void malformedCodeIsRejected(String malformed) throws Exception {
        verifyPickup(owner, bakery, malformed)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"));
    }
}
