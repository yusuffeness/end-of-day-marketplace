package tr.edu.etu.endofday.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** POST /orders: the order transaction of report section 7.2 and its rules. */
class OrderPlacementIntegrationTest extends MarketplaceIntegrationTest {

    private Account owner;
    private Account customer;
    private long bakery;

    @BeforeEach
    void createBusiness() throws Exception {
        owner = signUpOwner("siparis.sahip");
        customer = signUpCustomer("siparis.musteri");
        bakery = approvedBusiness(owner.id());
    }

    @Test
    void orderIsStoredWithItemsStockChargeAndPickupInOneGo() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);
        String requestId = newRequestId();

        JsonNode order = json(placeOrder(customer, bakery, requestId, items(listing, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(bakery))
                .andExpect(jsonPath("$.status").value("ONAYLANDI"))
                .andExpect(jsonPath("$.createdAt").value("2031-03-10T12:00:00"))
                .andExpect(jsonPath("$.cancelledAt").isEmpty())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].listingId").value(listing))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value("60.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("120.00"))
                .andExpect(jsonPath("$.totalAmount").value("120.00"))
                .andExpect(jsonPath("$.refundedAmount").value("0.00"))
                .andExpect(jsonPath("$.pickup.code").value(matchesPattern("^[0-9]{6}$")))
                .andExpect(jsonPath("$.pickup.windowStart").value("2031-03-10T19:00:00"))
                .andExpect(jsonPath("$.pickup.windowEnd").value("2031-03-10T20:00:00"))
                .andExpect(jsonPath("$.pickup.pickedUpAt").isEmpty())
                .andExpect(jsonPath("$.review").isEmpty())
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.canReview").value(false)));
        long orderId = order.get("id").asLong();

        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
        assertThat(remainingOf(listing)).isEqualTo(3);
        Map<String, Object> charge =
                db().sql("""
                        SELECT islem_turu, tutar, durum, istek_no, kaynak_islem_id
                        FROM ODEME_ISLEMI WHERE siparis_id = :orderId
                        """).param("orderId", orderId).query().singleRow();
        assertThat(charge.get("islem_turu")).isEqualTo("TAHSILAT");
        assertThat(charge.get("durum")).isEqualTo("BASARILI");
        assertThat((BigDecimal) charge.get("tutar")).isEqualByComparingTo("120.00");
        assertThat(charge.get("istek_no")).isEqualTo(requestId);
        assertThat(charge.get("kaynak_islem_id")).isNull();
        assertThat(pickupCodeOf(orderId))
                .isEqualTo(order.get("pickup").get("code").asString());
    }

    @Test
    void sameRequestIdReturnsTheFirstOrderAndCreatesNothingNew() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);
        String requestId = newRequestId();

        long first = json(placeOrder(customer, bakery, requestId, items(listing, 2))
                        .andExpect(status().isCreated()))
                .get("id")
                .asLong();
        placeOrder(customer, bakery, requestId, items(listing, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first));
        // Even a different body with the same request id returns the first order.
        placeOrder(customer, bakery, requestId, items(listing, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        assertThat(countOrdersOfBusiness(bakery)).isOne();
        assertThat(remainingOf(listing)).isEqualTo(3);
    }

    @Test
    void requestIdOfAnotherCustomerIsRejected() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);
        String requestId = newRequestId();
        placeOrder(customer, bakery, requestId, items(listing, 1)).andExpect(status().isCreated());
        Account other = signUpCustomer("siparis.diger");

        placeOrder(other, bakery, requestId, items(listing, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-018"));
        assertThat(countOrdersOfBusiness(bakery)).isOne();
    }

    @Test
    void listingOfAnotherBusinessIsRejected() throws Exception {
        long own = listing(bakery, "120.00", "60.00", 5);
        long otherBusiness = approvedBusiness(signUpOwner("siparis.baska").id());
        long foreign = listing(otherBusiness, "120.00", "60.00", 5);

        placeOrder(customer, bakery, newRequestId(), items(own, 1, foreign, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-001"));
        assertNoTrace(bakery, own, 5);
        assertThat(remainingOf(foreign)).isEqualTo(5);
    }

    @Test
    void insufficientStockIsRejected() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5, 2, WINDOW_START, WINDOW_END, "AKTIF");

        placeOrder(customer, bakery, newRequestId(), items(listing, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-014"));
        assertNoTrace(bakery, listing, 2);
    }

    @Test
    void listingsWithoutACommonPickupWindowAreRejected() throws Exception {
        long evening = listing(bakery, "120.00", "60.00", 5);
        long night = listing(bakery, "120.00", "60.00", 5, 5, WINDOW_END, WINDOW_END.plusHours(1), "AKTIF");

        placeOrder(customer, bakery, newRequestId(), items(evening, 1, night, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-015"));
        assertNoTrace(bakery, evening, 5);
    }

    @Test
    void pickupWindowOfSeveralListingsIsTheirIntersection() throws Exception {
        long first = listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START.minusHours(1), WINDOW_END, "AKTIF");
        long second = listing(bakery, "80.00", "35.00", 5, 5, WINDOW_START, WINDOW_END.plusHours(1), "AKTIF");

        placeOrder(customer, bakery, newRequestId(), items(first, 1, second, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickup.windowStart").value("2031-03-10T19:00:00"))
                .andExpect(jsonPath("$.pickup.windowEnd").value("2031-03-10T20:00:00"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalAmount").value("130.00"));
    }

    @Test
    void orderAfterThePickupWindowStartedIsRejected() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);

        clock.setTo(WINDOW_START);
        placeOrder(customer, bakery, newRequestId(), items(listing, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-016"));
        assertNoTrace(bakery, listing, 5);

        clock.setTo(WINDOW_START.minusSeconds(1));
        placeOrder(customer, bakery, newRequestId(), items(listing, 1)).andExpect(status().isCreated());
    }

    @Test
    void sameListingSelectedTwiceBecomesOneItem() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);

        long orderId = json(placeOrder(customer, bakery, newRequestId(), items(listing, 1, listing, 2))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.items", hasSize(1)))
                        .andExpect(jsonPath("$.items[0].quantity").value(3))
                        .andExpect(jsonPath("$.totalAmount").value("180.00")))
                .get("id")
                .asLong();

        assertThat(db().sql("SELECT COUNT(*) FROM SIPARIS_KALEMI WHERE siparis_id = :id")
                        .param("id", orderId)
                        .query(Long.class)
                        .single())
                .isOne();
        assertThat(remainingOf(listing)).isEqualTo(2);
    }

    @Test
    void unitPriceComesFromTheDatabaseAndStaysWhenTheListingPriceChanges() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);

        long orderId = json(customer.client()
                        .postJson("/api/v1/orders", """
                                {"businessId": %d, "requestId": "%s", "totalAmount": "1.00",
                                 "items": [{"listingId": %d, "quantity": 2, "unitPrice": "1.00"}]}
                                """.formatted(bakery, newRequestId(), listing))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.items[0].unitPrice").value("60.00"))
                        .andExpect(jsonPath("$.totalAmount").value("120.00")))
                .get("id")
                .asLong();

        db().sql("UPDATE PAKET_ILANI SET satis_fiyati = 50.00 WHERE ilan_id = :id")
                .param("id", listing)
                .update();
        getOrder(customer, orderId)
                .andExpect(jsonPath("$.items[0].unitPrice").value("60.00"))
                .andExpect(jsonPath("$.totalAmount").value("120.00"));
    }

    @Test
    void declinedPaymentLeavesNoTraceInAnyTable() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);
        String requestId = newRequestId();

        customer.client()
                .postJson("/api/v1/orders", """
                        {"businessId": %d, "requestId": "%s", "simulatePaymentFailure": true,
                         "items": [{"listingId": %d, "quantity": 2}]}
                        """.formatted(bakery, requestId, listing))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("EOD-017"));

        assertNoTrace(bakery, listing, 5);
        assertThat(db().sql("SELECT COUNT(*) FROM ODEME_ISLEMI WHERE istek_no = :requestId")
                        .param("requestId", requestId)
                        .query(Long.class)
                        .single())
                .isZero();
        // Nothing was stored, so the same request id can be used again.
        placeOrder(customer, bakery, requestId, items(listing, 2)).andExpect(status().isCreated());
    }

    @Test
    void inactiveListingOrInactiveBusinessIsRejected() throws Exception {
        long inactive = listing(bakery, "120.00", "60.00", 5, 5, WINDOW_START, WINDOW_END, "PASIF");
        placeOrder(customer, bakery, newRequestId(), items(inactive, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-013"));

        long listing = listing(bakery, "120.00", "60.00", 5);
        db().sql("UPDATE ISLETME SET aktif_mi = FALSE WHERE isletme_id = :id")
                .param("id", bakery)
                .update();
        placeOrder(customer, bakery, newRequestId(), items(listing, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-013"));
        assertNoTrace(bakery, listing, 5);
    }

    @Test
    void unknownListingOrBusinessIsNotFound() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);

        placeOrder(customer, bakery, newRequestId(), items(listing, 1, 999_999_999L, 1))
                .andExpect(status().isNotFound());
        placeOrder(customer, 999_999_999L, newRequestId(), items(listing, 1)).andExpect(status().isNotFound());
        assertNoTrace(bakery, listing, 5);
    }

    @Test
    void invalidOrderBodyIsRejectedWithFieldErrors() throws Exception {
        customer.client()
                .postJson("/api/v1/orders", """
                        {"businessId": %d, "requestId": " ", "items": [{"listingId": 1, "quantity": 0}]}
                        """.formatted(bakery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("items[0].quantity", "requestId")));
    }

    @Test
    void customerSeesOnlyTheirOwnOrders() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 5);
        long orderId = placedOrder(customer, bakery, listing, 1);
        Account other = signUpCustomer("siparis.merakli");

        getOrder(other, orderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EOD-404"));
        other.client()
                .perform(get("/api/v1/orders"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void myOrdersListsNewestFirstWithStatusFilter() throws Exception {
        long listing = listing(bakery, "120.00", "60.00", 10);
        long older = placedOrder(customer, bakery, listing, 1);
        clock.setTo(NOW.plusMinutes(5));
        long newer = placedOrder(customer, bakery, listing, 2);
        cancelOrder(customer, older, newRequestId(), false).andExpect(status().isOk());

        customer.client()
                .perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newer))
                .andExpect(jsonPath("$.items[0].totalAmount").value("120.00"))
                .andExpect(jsonPath("$.items[0].businessName").isString())
                .andExpect(jsonPath("$.items[0].pickup.code").isString())
                .andExpect(jsonPath("$.items[0].items").isEmpty())
                .andExpect(jsonPath("$.items[0].canCancel").value(true))
                .andExpect(jsonPath("$.items[1].id").value(older));
        customer.client()
                .perform(get("/api/v1/orders?status=IPTAL_EDILDI"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(older))
                .andExpect(jsonPath("$.items[0].canCancel").value(false));
    }

    @Test
    void reportExampleRunsFromListingToReviewThroughTheApi() throws Exception {
        // Report section 6.1: 5 units between 19:00 and 20:00, 120 TL -> 60 TL, the customer buys 2.
        long listingId = json(owner.client()
                        .postJson("/api/v1/businesses/" + bakery + "/listings", """
                                {"title": "Karışık Unlu Mamul Paketi", "description": "Ekmek, poğaça ve açma.",
                                 "allergenInfo": "Gluten, süt, yumurta içerir.", "normalPrice": "120.00",
                                 "salePrice": "60.00", "totalQuantity": 5,
                                 "pickupStart": "2031-03-10T19:00:00", "pickupEnd": "2031-03-10T20:00:00"}
                                """)
                        .andExpect(status().isCreated()))
                .get("id")
                .asLong();

        JsonNode order = json(placeOrder(customer, bakery, newRequestId(), items(listingId, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value("120.00")));
        long orderId = order.get("id").asLong();
        customer.client()
                .perform(get("/api/v1/listings/" + listingId))
                .andExpect(jsonPath("$.remainingQuantity").value(3));

        clock.setTo(LocalDateTime.of(2031, 3, 10, 19, 10));
        verifyPickup(owner, bakery, order.get("pickup").get("code").asString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TESLIM_EDILDI"))
                .andExpect(jsonPath("$.pickup.pickedUpAt").value("2031-03-10T19:10:00"));

        customer.client()
                .postJson("/api/v1/orders/" + orderId + "/review", """
                        {"rating": 5, "comment": "Çok taze ve lezzetliydi."}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));
        getOrder(customer, orderId)
                .andExpect(jsonPath("$.status").value("TESLIM_EDILDI"))
                .andExpect(jsonPath("$.review.rating").value(5))
                .andExpect(jsonPath("$.review.comment").value("Çok taze ve lezzetliydi."))
                .andExpect(jsonPath("$.canCancel").value(false))
                .andExpect(jsonPath("$.canReview").value(false));
    }

    private void assertNoTrace(long businessId, long listingId, int expectedRemaining) {
        assertThat(countOrdersOfBusiness(businessId)).isZero();
        assertThat(db().sql("""
                        SELECT (SELECT COUNT(*) FROM SIPARIS_KALEMI WHERE ilan_id = :listingId)
                             + (SELECT COUNT(*) FROM ODEME_ISLEMI o JOIN SIPARIS s ON s.siparis_id = o.siparis_id
                                WHERE s.isletme_id = :businessId)
                             + (SELECT COUNT(*) FROM TESLIM_ALMA t JOIN SIPARIS s ON s.siparis_id = t.siparis_id
                                WHERE s.isletme_id = :businessId)
                        """)
                        .param("listingId", listingId)
                        .param("businessId", businessId)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(remainingOf(listingId)).isEqualTo(expectedRemaining);
    }

    private static String items(long listingId, int quantity) {
        return "[{\"listingId\": %d, \"quantity\": %d}]".formatted(listingId, quantity);
    }

    private static String items(long firstId, int firstQuantity, long secondId, int secondQuantity) {
        return "[{\"listingId\": %d, \"quantity\": %d}, {\"listingId\": %d, \"quantity\": %d}]"
                .formatted(firstId, firstQuantity, secondId, secondQuantity);
    }
}
