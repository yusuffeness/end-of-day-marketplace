package tr.edu.etu.endofday.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** POST /orders/{id}/review: only the customer, only delivered orders, only once. */
class ReviewIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private Account customer;
    private long bakery;
    private long orderId;

    @BeforeEach
    void placeOrder() throws Exception {
        owner = signUpOwner("puan.sahip");
        customer = signUpCustomer("puan.musteri");
        bakery = approvedBusiness(owner.id());
        orderId = placedOrder(customer, bakery, listing(bakery, "120.00", "60.00", 5), 1);
    }

    @Test
    void customerReviewsTheDeliveredOrder() throws Exception {
        deliver();

        review(customer, orderId, """
                        {"rating": 4, "comment": "  Taze ve güzeldi.  "}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Taze ve güzeldi."))
                .andExpect(jsonPath("$.createdAt").value("2031-03-10T19:30:00"));
        getOrder(customer, orderId)
                .andExpect(jsonPath("$.review.rating").value(4))
                .andExpect(jsonPath("$.canReview").value(false));
    }

    @Test
    void commentIsOptional() throws Exception {
        deliver();

        review(customer, orderId, "{\"rating\": 5, \"comment\": \"   \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").isEmpty());
        assertThat(db().sql("SELECT yorum IS NULL FROM DEGERLENDIRME WHERE siparis_id = :id")
                        .param("id", orderId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void orderThatIsNotDeliveredCannotBeReviewed() throws Exception {
        review(customer, orderId, "{\"rating\": 5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-006"));
    }

    @Test
    void secondReviewIsRejected() throws Exception {
        deliver();
        review(customer, orderId, "{\"rating\": 5}").andExpect(status().isCreated());

        review(customer, orderId, "{\"rating\": 1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-024"));
        assertThat(db().sql("SELECT puan FROM DEGERLENDIRME WHERE siparis_id = :id")
                        .param("id", orderId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
    }

    @Test
    void anotherCustomerCannotReviewTheOrder() throws Exception {
        deliver();
        Account other = signUpCustomer("puan.diger");

        review(other, orderId, "{\"rating\": 1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-404"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"rating\": 0}", "{\"rating\": 6}", "{}"})
    void ratingMustBeBetweenOneAndFive(String body) throws Exception {
        deliver();

        review(customer, orderId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"));
    }

    private void deliver() throws Exception {
        clock.setTo(WINDOW_START.plusMinutes(10));
        verifyPickup(owner, bakery, pickupCodeOf(orderId)).andExpect(status().isOk());
        clock.setTo(WINDOW_START.plusMinutes(30));
    }

    private static ResultActions review(Account account, long orderId, String body) throws Exception {
        return account.client().postJson("/api/v1/orders/" + orderId + "/review", body);
    }
}
