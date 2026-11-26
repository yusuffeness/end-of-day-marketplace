package tr.edu.etu.endofday.listing;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** GET /listings and GET /listings/{id}: only purchasable listings, filters, pagination and visibility. */
class ListingIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private Account customer;
    private long bakery;

    @BeforeEach
    void createBusiness() throws Exception {
        owner = signUpOwner("ilan.sahip");
        customer = signUpCustomer("ilan.musteri");
        bakery = approvedBusiness(owner.id());
    }

    @Test
    void searchListsOnlyPurchasableListings() throws Exception {
        long purchasable = listing(bakery, "120.00", "60.00", 5);
        listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START, WINDOW_END, "PASIF");
        listing(bakery, "120.00", "60.00", 5, 0, WINDOW_START, WINDOW_END, "AKTIF");
        listing(bakery, "120.00", "60.00", 5, 5, NOW.minusHours(1), WINDOW_END, "AKTIF");
        listing(bakery, "120.00", "60.00", 5, 5, NOW, WINDOW_END, "AKTIF");
        long deactivatedBusiness = approvedBusiness(owner.id());
        listing(deactivatedBusiness, "120.00", "60.00", 5);
        db().sql("UPDATE ISLETME SET aktif_mi = FALSE WHERE isletme_id = :id")
                .param("id", deactivatedBusiness)
                .update();
        long rejectedBusiness = approvedBusiness(owner.id());
        listing(rejectedBusiness, "120.00", "60.00", 5);
        db().sql("UPDATE ISLETME SET onay_durumu = 'REDDEDILDI' WHERE isletme_id = :id")
                .param("id", rejectedBusiness)
                .update();

        search(customer.client(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) purchasable)))
                .andExpect(jsonPath("$.items[0].businessId").value(bakery))
                .andExpect(jsonPath("$.items[0].businessType").value("FIRIN"))
                .andExpect(jsonPath("$.items[0].city").value(city))
                .andExpect(jsonPath("$.items[0].salePrice").value("60.00"))
                .andExpect(jsonPath("$.items[0].normalPrice").value("120.00"))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(5))
                .andExpect(jsonPath("$.items[0].pickupStart").value("2031-03-10T19:00:00"))
                .andExpect(jsonPath("$.items[0].pickupEnd").value("2031-03-10T20:00:00"));
    }

    @Test
    void listingDisappearsWhenItsPickupWindowStarts() throws Exception {
        listing(bakery, "120.00", "60.00", 5);
        search(customer.client(), "").andExpect(jsonPath("$.totalItems").value(1));

        clock.setTo(WINDOW_START.minusSeconds(1));
        search(customer.client(), "").andExpect(jsonPath("$.totalItems").value(1));

        clock.advance(Duration.ofSeconds(1));
        search(customer.client(), "").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void searchFiltersByDistrictTypeAndPriceRange() throws Exception {
        long cheapBread = listing(bakery, "80.00", "30.00", 5);
        long bread = listing(bakery, "120.00", "60.00", 5);
        long cafe = business(owner.id(), "Keçiören", "KAFE", "ONAYLANDI", true);
        long cake = listing(cafe, "150.00", "70.00", 5);

        search(customer.client(), "&district=Keçiören")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) cake)));
        search(customer.client(), "&type=FIRIN")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) cheapBread, (int) bread)));
        search(customer.client(), "&minPrice=30.00&maxPrice=60.00")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) cheapBread, (int) bread)));
        search(customer.client(), "&minPrice=60.01")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) cake)));
        search(customer.client(), "&district=Çankaya&type=FIRIN&maxPrice=59.99")
                .andExpect(jsonPath("$.items[*].id").value(contains((int) cheapBread)));
        customer.client()
                .perform(get("/api/v1/listings").param("city", "Başka Şehir " + city))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void searchIsPaginatedInPickupStartOrder() throws Exception {
        long late =
                listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START.plusHours(1), WINDOW_END.plusHours(1), "AKTIF");
        long early = listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START.minusHours(1), WINDOW_END, "AKTIF");
        long middle = listing(bakery, "120.00", "60.00", 5);

        search(customer.client(), "&page=0&size=2")
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) early, (int) middle)));
        search(customer.client(), "&page=1&size=2")
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) late)));
        search(customer.client(), "&page=2&size=2").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"&size=0", "&size=101", "&page=-1", "&minPrice=70.00&maxPrice=60.00", "&type=PASTANE"})
    void invalidSearchParametersAreRejected(String query) throws Exception {
        search(customer.client(), query)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"));
    }

    @Test
    void searchRequiresSignIn() throws Exception {
        new ApiClient(mockMvc)
                .perform(get("/api/v1/listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-401"));
    }

    @Test
    void detailShowsDescriptionAllergensPricesStockAddressAndWindow() throws Exception {
        long id = listing(bakery, "120.00", "60.00", 5, 3, WINDOW_START, WINDOW_END, "AKTIF");

        customer.client()
                .perform(get("/api/v1/listings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.description").value("Günün ürünlerinden karışık paket."))
                .andExpect(jsonPath("$.allergenInfo").value("Gluten içerir."))
                .andExpect(jsonPath("$.normalPrice").value("120.00"))
                .andExpect(jsonPath("$.salePrice").value("60.00"))
                .andExpect(jsonPath("$.totalQuantity").value(5))
                .andExpect(jsonPath("$.remainingQuantity").value(3))
                .andExpect(jsonPath("$.businessName").isString())
                .andExpect(jsonPath("$.address").value("Test Cad. No: 1"))
                .andExpect(jsonPath("$.phone").value("0312 555 00 00"))
                .andExpect(jsonPath("$.pickupStart").value("2031-03-10T19:00:00"))
                .andExpect(jsonPath("$.pickupEnd").value("2031-03-10T20:00:00"))
                .andExpect(jsonPath("$.status").value("AKTIF"));
    }

    @Test
    void inactiveListingIsVisibleOnlyToTheOwner() throws Exception {
        long id = listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START, WINDOW_END, "PASIF");

        customer.client()
                .perform(get("/api/v1/listings/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-404"));
        owner.client()
                .perform(get("/api/v1/listings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASIF"));
    }

    @Test
    void unknownListingIsNotFound() throws Exception {
        customer.client().perform(get("/api/v1/listings/999999999")).andExpect(status().isNotFound());
    }

    private ResultActions search(ApiClient client, String query) throws Exception {
        return client.perform(get("/api/v1/listings?city=" + city + query));
    }
}
