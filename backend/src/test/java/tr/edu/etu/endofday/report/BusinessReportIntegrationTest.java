package tr.edu.etu.endofday.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/**
 * GET /businesses/{id}/reports through the API. The figures of the demo data are checked by
 * BusinessReportDemoDataIntegrationTest against values computed by hand.
 */
class BusinessReportIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private long bakery;

    @BeforeEach
    void createBusiness() throws Exception {
        owner = signUpOwner("rapor.sahip");
        bakery = approvedBusiness(owner.id());
    }

    @Test
    void reportCountsOrdersExcludesCancelledUnitsAndNetsRefunds() throws Exception {
        Account customer = signUpCustomer("rapor.musteri");
        long listing = listing(bakery, "120.00", "60.00", 20);
        long delivered = placedOrder(customer, bakery, listing, 2);
        long cancelled = placedOrder(customer, bakery, listing, 3);
        long missed = placedOrder(customer, bakery, listing, 1);
        placedOrder(customer, bakery, listing, 1);
        cancelOrder(customer, cancelled, newRequestId(), false).andExpect(status().isOk());
        clock.setTo(WINDOW_START.plusMinutes(10));
        verifyPickup(owner, bakery, pickupCodeOf(delivered)).andExpect(status().isOk());
        customer.client()
                .postJson("/api/v1/orders/" + delivered + "/review", "{\"rating\": 4}")
                .andExpect(status().isCreated());
        db().sql("UPDATE SIPARIS SET durum = 'TESLIM_ALINMADI' WHERE siparis_id = :id")
                .param("id", missed)
                .update();

        report(owner, bakery, "2031-03-10", "2031-03-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(bakery))
                .andExpect(jsonPath("$.from").value("2031-03-10"))
                .andExpect(jsonPath("$.to").value("2031-03-10"))
                .andExpect(jsonPath("$.orderCount").value(4))
                .andExpect(jsonPath("$.deliveredCount").value(1))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.notPickedUpCount").value(1))
                // 2 + 1 + 1: the 3 units of the cancelled order are not counted.
                .andExpect(jsonPath("$.unitsSold").value(4))
                // Charges 120 + 180 + 60 + 60, refund 180.
                .andExpect(jsonPath("$.grossRevenue").value("420.00"))
                .andExpect(jsonPath("$.refundedAmount").value("180.00"))
                .andExpect(jsonPath("$.netRevenue").value("240.00"))
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.averageRating").value("4.00"))
                .andExpect(jsonPath("$.ratingDistribution.4").value(1))
                .andExpect(jsonPath("$.ratingDistribution.5").value(0));
    }

    @Test
    void ordersOutsideTheRangeAreNotCounted() throws Exception {
        Account customer = signUpCustomer("rapor.aralik");
        long listing = listing(bakery, "120.00", "60.00", 5);
        placedOrder(customer, bakery, listing, 1);

        report(owner, bakery, "2031-03-11", "2031-03-31")
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.grossRevenue").value("0.00"))
                .andExpect(jsonPath("$.averageRating").isEmpty())
                .andExpect(jsonPath("$.ratingCount").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?from=2031-03-11&to=2031-03-10", "?from=2030-01-01&to=2031-03-10"})
    void invalidRangeIsRejected(String query) throws Exception {
        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/reports" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-030"));
    }

    @Test
    void rangeIsRequired() throws Exception {
        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/reports?from=2031-03-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"));
    }

    @Test
    void reportOfAnotherBusinessIsForbidden() throws Exception {
        Account other = signUpOwner("rapor.baska");

        report(other, bakery, "2031-03-01", "2031-03-31")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    private static ResultActions report(Account account, long businessId, String from, String to) throws Exception {
        return account.client()
                .perform(get("/api/v1/businesses/" + businessId + "/reports?from=" + from + "&to=" + to));
    }
}
