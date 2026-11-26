package tr.edu.etu.endofday.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** GET /businesses/{id}/orders: the owner's view of the orders, without the pickup code and personal data. */
class BusinessOrdersIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private long bakery;
    private long todayListing;

    @BeforeEach
    void createBusiness() throws Exception {
        owner = signUpOwner("isletme.siparis");
        bakery = approvedBusiness(owner.id());
        todayListing = listing(bakery, "120.00", "60.00", 10);
    }

    @Test
    void ordersOfTheDayHideThePickupCodeAndShowTheCustomerAsFirstNameAndInitial() throws Exception {
        Account customer = signUpCustomer("isletme.ahmet", "Ahmet", "Yılmaz");
        long orderId = placedOrder(customer, bakery, todayListing, 2);
        String code = pickupCodeOf(orderId);

        ResultActions response = orders(owner, bakery, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].status").value("ONAYLANDI"))
                .andExpect(jsonPath("$.items[0].customerName").value("Ahmet Y."))
                .andExpect(jsonPath("$.items[0].items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].totalAmount").value("120.00"))
                .andExpect(jsonPath("$.items[0].pickup.windowStart").value("2031-03-10T19:00:00"))
                .andExpect(jsonPath("$.items[0].pickup.code").doesNotExist());

        String body = response.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContain(code)
                .doesNotContain(customer.email())
                .doesNotContain("Yılmaz")
                .doesNotContainIgnoringCase("phone")
                .doesNotContainIgnoringCase("email");
    }

    @Test
    void initialOfTheLastNameUsesTurkishCasing() throws Exception {
        Account customer = signUpCustomer("isletme.ilhan", "Zeynep", "ilhan");
        placedOrder(customer, bakery, todayListing, 1);

        orders(owner, bakery, "").andExpect(jsonPath("$.items[0].customerName").value("Zeynep İ."));
    }

    @Test
    void ordersAreFilteredByPickupDayAndStatus() throws Exception {
        Account customer = signUpCustomer("isletme.filtre");
        long tomorrowListing =
                listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START.plusDays(1), WINDOW_END.plusDays(1), "AKTIF");
        long today = placedOrder(customer, bakery, todayListing, 1);
        long cancelled = placedOrder(customer, bakery, todayListing, 1);
        long tomorrow = placedOrder(customer, bakery, tomorrowListing, 1);
        cancelOrder(customer, cancelled, newRequestId(), false).andExpect(status().isOk());

        orders(owner, bakery, "").andExpect(jsonPath("$.items[*].id").value(contains((int) today, (int) cancelled)));
        orders(owner, bakery, "?date=2031-03-11")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) tomorrow)));
        orders(owner, bakery, "?status=IPTAL_EDILDI")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) cancelled)))
                .andExpect(jsonPath("$.items[0].refundedAmount").value("60.00"));
    }

    @Test
    void ordersOfTheDayThatWereNotPickedUpCanBeListed() throws Exception {
        Account customer = signUpCustomer("isletme.alinmadi");
        long picked = placedOrder(customer, bakery, todayListing, 1);
        long missed = placedOrder(customer, bakery, todayListing, 1);
        clock.setTo(WINDOW_START.plusMinutes(5));
        verifyPickup(owner, bakery, pickupCodeOf(picked)).andExpect(status().isOk());
        // The database event marks orders by the real time, so the test sets the status the event would set.
        db().sql("UPDATE SIPARIS SET durum = 'TESLIM_ALINMADI' WHERE siparis_id = :id")
                .param("id", missed)
                .update();

        orders(owner, bakery, "?status=TESLIM_ALINMADI")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(missed));
        orders(owner, bakery, "?status=TESLIM_EDILDI")
                .andExpect(jsonPath("$.items[0].pickup.pickedUpAt").value("2031-03-10T19:05:00"));
    }

    @Test
    void ordersOfAnotherBusinessAreForbidden() throws Exception {
        Account otherOwner = signUpOwner("isletme.baska");
        approvedBusiness(otherOwner.id());

        orders(otherOwner, bakery, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
        orders(owner, 999_999_999L, "").andExpect(status().isNotFound());
    }

    private static ResultActions orders(Account account, long businessId, String query) throws Exception {
        return account.client().perform(get("/api/v1/businesses/" + businessId + "/orders" + query));
    }
}
