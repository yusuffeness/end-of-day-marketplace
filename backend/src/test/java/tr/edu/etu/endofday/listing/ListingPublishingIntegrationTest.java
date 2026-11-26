package tr.edu.etu.endofday.listing;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** POST /businesses/{id}/listings by the owner of the business. */
class ListingPublishingIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private long bakery;

    @BeforeEach
    void createBusiness() throws Exception {
        owner = signUpOwner("yayin.sahip");
        bakery = approvedBusiness(owner.id());
    }

    @Test
    void ownerPublishesTheListingOfTheReportExample() throws Exception {
        publish(owner, bakery, "120.00", "60.00", 5, "2031-03-10T19:00:00", "2031-03-10T20:00:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(bakery))
                .andExpect(jsonPath("$.normalPrice").value("120.00"))
                .andExpect(jsonPath("$.salePrice").value("60.00"))
                .andExpect(jsonPath("$.totalQuantity").value(5))
                .andExpect(jsonPath("$.remainingQuantity").value(5))
                .andExpect(jsonPath("$.status").value("AKTIF"))
                .andExpect(jsonPath("$.createdAt").value("2031-03-10T12:00:00"));
    }

    @Test
    void ownerOfAnotherBusinessCannotPublish() throws Exception {
        Account other = signUpOwner("yayin.baska");

        publish(other, bakery, "120.00", "60.00", 5, "2031-03-10T19:00:00", "2031-03-10T20:00:00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    @Test
    void customerWithoutTheOwnerRoleCannotPublish() throws Exception {
        Account customer = signUpCustomer("yayin.musteri");

        publish(customer, bakery, "120.00", "60.00", 5, "2031-03-10T19:00:00", "2031-03-10T20:00:00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    @Test
    void pendingBusinessCannotPublish() throws Exception {
        long pending = business(owner.id(), "Çankaya", "FIRIN", "BEKLEMEDE", true);

        publish(owner, pending, "120.00", "60.00", 5, "2031-03-10T19:00:00", "2031-03-10T20:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-007"));
    }

    @Test
    void salePriceMustBeLowerThanTheNormalPrice() throws Exception {
        publish(owner, bakery, "60.00", "60.00", 5, "2031-03-10T19:00:00", "2031-03-10T20:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(jsonPath("$.errors[*].message")
                        .value(hasItem("Satış fiyatı sıfırdan büyük ve normal fiyattan düşük olmalıdır.")));
    }

    @Test
    void pickupWindowMustStartBeforeItEndsAndEndInTheFuture() throws Exception {
        publish(owner, bakery, "120.00", "60.00", 5, "2031-03-10T20:00:00", "2031-03-10T19:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-025"));
        publish(owner, bakery, "120.00", "60.00", 5, "2031-03-10T10:00:00", "2031-03-10T11:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-025"));
    }

    private ResultActions publish(
            Account account, long businessId, String normal, String sale, int quantity, String start, String end)
            throws Exception {
        return account.client().postJson("/api/v1/businesses/" + businessId + "/listings", """
                {"title": "Karışık Unlu Mamul Paketi", "description": "Ekmek, poğaça ve açma.",
                 "allergenInfo": "Gluten, süt, yumurta içerir.", "normalPrice": "%s", "salePrice": "%s",
                 "totalQuantity": %d, "pickupStart": "%s", "pickupEnd": "%s"}
                """.formatted(
                        normal, sale, quantity, start, end));
    }
}
