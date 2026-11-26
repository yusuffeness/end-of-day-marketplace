package tr.edu.etu.endofday.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/**
 * Concurrent orders against the real MySQL locks: the listing rows are locked in id order, so customers racing for
 * the last unit are served one after the other and exactly one of them gets it.
 */
class OrderConcurrencyIntegrationTest extends MarketplaceIntegrationTest {

    private static final int CUSTOMERS = 10;
    private static final int ROUNDS = 10;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void tenCustomersRaceForTheLastUnitAndExactlyOneGetsItInEveryRound() throws Exception {
        Account owner = signUpOwner("yaris.sahip");
        long bakery = approvedBusiness(owner.id());
        List<Account> customers = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS; i++) {
            customers.add(signUpCustomer("yaris" + i));
        }

        for (int round = 1; round <= ROUNDS; round++) {
            long listing = listing(bakery, "120.00", "60.00", 5, 1, WINDOW_START, WINDOW_END, "AKTIF");
            List<Callable<MockHttpServletResponse>> attempts = new ArrayList<>();
            for (Account customer : customers) {
                attempts.add(() ->
                        placeOrder(customer, bakery, listing, 1).andReturn().getResponse());
            }

            List<MockHttpServletResponse> responses = runTogether(attempts);

            long created = responses.stream().filter(r -> r.getStatus() == 201).count();
            List<String> rejections = responses.stream()
                    .filter(r -> r.getStatus() != 201)
                    .map(OrderConcurrencyIntegrationTest::statusAndCode)
                    .toList();
            assertThat(created).as("orders created in round %d", round).isOne();
            assertThat(rejections)
                    .as("rejections in round %d", round)
                    .hasSize(CUSTOMERS - 1)
                    .containsOnly("409 EOD-014");
            assertThat(remainingOf(listing)).isZero();
            assertThat(db().sql("SELECT COUNT(*) FROM SIPARIS_KALEMI WHERE ilan_id = :id")
                            .param("id", listing)
                            .query(Long.class)
                            .single())
                    .isOne();
        }
        assertThat(countOrdersOfBusiness(bakery)).isEqualTo(ROUNDS);
    }

    @Test
    void concurrentRequestsWithTheSameRequestIdCreateOneOrder() throws Exception {
        Account owner = signUpOwner("ayni.sahip");
        long bakery = approvedBusiness(owner.id());
        long listing = listing(bakery, "120.00", "60.00", 5);
        Account customer = signUpCustomer("ayni.musteri");
        String requestId = newRequestId();
        String items = "[{\"listingId\": %d, \"quantity\": 1}]".formatted(listing);

        List<Callable<MockHttpServletResponse>> attempts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Account tab = new Account(
                    customer.id(), customer.email(), customer.client().copy());
            attempts.add(
                    () -> placeOrder(tab, bakery, requestId, items).andReturn().getResponse());
        }
        List<MockHttpServletResponse> responses = runTogether(attempts);

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(201));
        assertThat(responses.stream()
                        .map(OrderConcurrencyIntegrationTest::orderId)
                        .distinct())
                .hasSize(1);
        assertThat(countOrdersOfBusiness(bakery)).isOne();
        assertThat(remainingOf(listing)).isEqualTo(4);
    }

    /** Starts all calls at the same moment and waits for every result. */
    private static <T> List<T> runTogether(List<Callable<T>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static String statusAndCode(MockHttpServletResponse response) {
        return response.getStatus() + " " + body(response).path("code").asString();
    }

    private static long orderId(MockHttpServletResponse response) {
        return body(response).get("id").asLong();
    }

    private static JsonNode body(MockHttpServletResponse response) {
        try {
            return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
