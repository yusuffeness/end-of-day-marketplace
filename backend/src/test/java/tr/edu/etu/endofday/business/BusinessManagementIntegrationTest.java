package tr.edu.etu.endofday.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** The owner's management of a business: details, listings, prices and deactivation. */
class BusinessManagementIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private Account otherOwner;
    private long bakery;
    private long otherBakery;

    @BeforeEach
    void createBusinesses() throws Exception {
        owner = signUpOwner("yonetim.sahip");
        otherOwner = signUpOwner("yonetim.baska");
        bakery = approvedBusiness(owner.id());
        otherBakery = approvedBusiness(otherOwner.id());
    }

    @Test
    void ownerUpdatesTheBusinessDetails() throws Exception {
        update(owner, bakery)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yeni Ad Fırını"))
                .andExpect(jsonPath("$.district").value("Yenimahalle"))
                .andExpect(jsonPath("$.type").value("FIRIN"))
                .andExpect(jsonPath("$.approvalStatus").value("ONAYLANDI"));
    }

    @Test
    void ownerCannotChangeAnotherBusinessOrItsListings() throws Exception {
        long foreignListing = listing(otherBakery, "120.00", "60.00", 5);

        update(owner, otherBakery)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
        changePrice(owner, otherBakery, foreignListing, "50.00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
        deactivate(owner, otherBakery, foreignListing).andExpect(status().isForbidden());
        publish(owner, otherBakery, "2031-03-10T19:00:00", "2031-03-10T20:00:00", "60.00")
                .andExpect(status().isForbidden());
        owner.client()
                .perform(get("/api/v1/businesses/" + otherBakery + "/listings"))
                .andExpect(status().isForbidden());
        // Through the own business, a listing of another business is unknown.
        changePrice(owner, bakery, foreignListing, "50.00").andExpect(status().isNotFound());
        deactivate(owner, bakery, foreignListing).andExpect(status().isNotFound());

        assertThat(db().sql("SELECT CONCAT(satis_fiyati, ' ', durum) FROM PAKET_ILANI WHERE ilan_id = :id")
                        .param("id", foreignListing)
                        .query(String.class)
                        .single())
                .isEqualTo("60.00 AKTIF");
    }

    @ParameterizedTest(name = "{0} - {1}")
    @CsvSource({
        // starts in the past
        "2031-03-10T11:00:00, 2031-03-10T13:00:00",
        // starts now
        "2031-03-10T12:00:00, 2031-03-10T13:00:00",
        // ends before it starts
        "2031-03-10T20:00:00, 2031-03-10T19:00:00",
        // ends on the next day
        "2031-03-10T23:00:00, 2031-03-11T00:30:00",
        // ends exactly at midnight, which is the next day
        "2031-03-10T23:00:00, 2031-03-11T00:00:00"
    })
    void listingWindowMustStartInTheFutureAndEndOnTheSameDay(String start, String end) throws Exception {
        publish(owner, bakery, start, end, "60.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-025"));
    }

    @Test
    void listingOnALaterDayIsAccepted() throws Exception {
        publish(owner, bakery, "2031-03-11T08:00:00", "2031-03-11T09:00:00", "60.00")
                .andExpect(status().isCreated());
    }

    @Test
    void salePriceMustBeBelowTheNormalPrice() throws Exception {
        publish(owner, bakery, "2031-03-10T19:00:00", "2031-03-10T20:00:00", "120.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message")
                        .value(hasItem("Satış fiyatı sıfırdan büyük ve normal fiyattan düşük olmalıdır.")));
        long listing = listing(bakery, "120.00", "60.00", 5);
        changePrice(owner, bakery, listing, "120.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-029"));
        changePrice(owner, bakery, listing, "0.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-029"));
        changePrice(owner, bakery, listing, "abc").andExpect(status().isBadRequest());
    }

    @Test
    void unapprovedOrInactiveBusinessCannotPublish() throws Exception {
        long pending = business(owner.id(), "Çankaya", "FIRIN", "BEKLEMEDE", true);
        long inactive = business(owner.id(), "Çankaya", "FIRIN", "ONAYLANDI", false);

        publish(owner, pending, "2031-03-10T19:00:00", "2031-03-10T20:00:00", "60.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-007"));
        publish(owner, inactive, "2031-03-10T19:00:00", "2031-03-10T20:00:00", "60.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-007"));
    }

    @Test
    void newPriceAppliesToNewOrdersOnly() throws Exception {
        Account customer = signUpCustomer("yonetim.musteri");
        long listing = listing(bakery, "120.00", "60.00", 5);
        long oldOrder = placedOrder(customer, bakery, listing, 2);

        changePrice(owner, bakery, listing, "45.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salePrice").value("45.00"))
                .andExpect(jsonPath("$.normalPrice").value("120.00"));
        long newOrder = placedOrder(customer, bakery, listing, 1);

        getOrder(customer, oldOrder)
                .andExpect(jsonPath("$.items[0].unitPrice").value("60.00"))
                .andExpect(jsonPath("$.totalAmount").value("120.00"));
        getOrder(customer, newOrder)
                .andExpect(jsonPath("$.items[0].unitPrice").value("45.00"))
                .andExpect(jsonPath("$.totalAmount").value("45.00"));
    }

    @Test
    void deactivatedListingLeavesTheSearchAndTakesNoOrders() throws Exception {
        Account customer = signUpCustomer("yonetim.alici");
        long listing = listing(bakery, "120.00", "60.00", 5);
        long confirmed = placedOrder(customer, bakery, listing, 1);

        deactivate(owner, bakery, listing)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASIF"));
        // Repeating it has no further effect.
        deactivate(owner, bakery, listing)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASIF"));

        customer.client()
                .perform(get("/api/v1/listings?city=" + city))
                .andExpect(jsonPath("$.totalItems").value(0));
        placeOrder(customer, bakery, listing, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-013"));
        // The confirmed order is not affected.
        assertThat(orderStatusOf(confirmed)).isEqualTo("ONAYLANDI");
    }

    @Test
    void ownerListsActiveAndInactiveListingsOfTheBusiness() throws Exception {
        long active = listing(bakery, "120.00", "60.00", 5);
        long inactive = listing(bakery, "80.00", "30.00", 3, 3, WINDOW_START, WINDOW_END, "PASIF");
        listing(otherBakery, "120.00", "60.00", 5);

        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) inactive, (int) active)));
        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/listings?status=PASIF"))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) inactive)));
    }

    @Test
    void customerWithoutTheOwnerRoleCannotManage() throws Exception {
        Account customer = signUpCustomer("yonetim.yetkisiz");

        update(customer, bakery).andExpect(status().isForbidden());
        customer.client()
                .perform(get("/api/v1/businesses/" + bakery + "/listings"))
                .andExpect(status().isForbidden());
    }

    private ResultActions update(Account account, long businessId) throws Exception {
        return account.client()
                .perform(put("/api/v1/businesses/" + businessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Yeni Ad Fırını", "type": "KAFE", "city": "%s", "district": "Yenimahalle",
                                 "address": "Yeni Adres No: 2", "phone": "0312 555 99 99"}
                                """.formatted(city)));
    }

    private ResultActions publish(Account account, long businessId, String start, String end, String salePrice)
            throws Exception {
        return account.client()
                .postJson("/api/v1/businesses/" + businessId + "/listings", """
                {"title": "Akşam Paketi", "description": "Günün ürünleri.", "allergenInfo": "Gluten içerir.",
                 "normalPrice": "120.00", "salePrice": "%s", "totalQuantity": 5,
                 "pickupStart": "%s", "pickupEnd": "%s"}
                """.formatted(salePrice, start, end));
    }

    private ResultActions changePrice(Account account, long businessId, long listingId, String price) throws Exception {
        return account.client()
                .perform(put("/api/v1/businesses/" + businessId + "/listings/" + listingId + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salePrice\": \"%s\"}".formatted(price)));
    }

    private ResultActions deactivate(Account account, long businessId, long listingId) throws Exception {
        return account.client()
                .perform(post("/api/v1/businesses/" + businessId + "/listings/" + listingId + "/deactivate"));
    }
}
