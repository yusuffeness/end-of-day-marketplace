package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.edu.etu.endofday.schema.SqlErrorAssertions.assertIsRuleViolation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.OwnerJdbc;

/**
 * Proves that the payment rules hold under concurrency: two eod_app connections insert conflicting payments for the
 * same order at the same moment, and the row lock taken by the trigger on SIPARIS lets exactly one of them through.
 *
 * <p>The tests are deliberately not transactional: each insert commits on its own pooled connection, as it would in
 * production. Each repetition creates its own rows and removes them afterwards through the eod_owner connection,
 * because eod_app has no DELETE privilege on these tables.
 */
class TriggerConcurrencyIntegrationTest extends IntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private JdbcClient jdbcClient;

    private SchemaTestData data;
    private long customerId;
    private long businessOwnerId;
    private long businessId;
    private long orderId;

    @BeforeEach
    void createOrder() {
        data = new SchemaTestData(jdbcClient);
        customerId = data.createUser();
        businessOwnerId = data.createUser();
        businessId = data.insertBusiness(data.businessRow(businessOwnerId));
        orderId = data.createOrder(customerId, businessId);
    }

    @AfterEach
    void removeOrder() {
        JdbcClient owner = OwnerJdbc.client();
        // Refunds first: they reference the charge.
        owner.sql("DELETE FROM ODEME_ISLEMI WHERE siparis_id = :id AND kaynak_islem_id IS NOT NULL")
                .param("id", orderId)
                .update();
        owner.sql("DELETE FROM ODEME_ISLEMI WHERE siparis_id = :id")
                .param("id", orderId)
                .update();
        owner.sql("DELETE FROM SIPARIS WHERE siparis_id = :id")
                .param("id", orderId)
                .update();
        owner.sql("DELETE FROM ISLETME WHERE isletme_id = :id")
                .param("id", businessId)
                .update();
        owner.sql("DELETE FROM KULLANICI WHERE kullanici_id IN (:ids)")
                .param("ids", List.of(customerId, businessOwnerId))
                .update();
    }

    @RepeatedTest(10)
    void acceptsExactlyOneOfTwoConcurrentSuccessfulCharges() throws Exception {
        Map<String, Object> first = payment("TAHSILAT", "100.00", null);
        Map<String, Object> second = payment("TAHSILAT", "100.00", null);

        List<Throwable> failures = runConcurrently(() -> data.insertPayment(first), () -> data.insertPayment(second));

        assertExactlyOneRejectedWith("EOD-002", failures);
        assertThat(paymentCount("TAHSILAT")).isEqualTo(1);
    }

    @RepeatedTest(10)
    void acceptsExactlyOneOfTwoConcurrentRefundsThatTogetherExceedTheCharge() throws Exception {
        long chargeId = data.insertPayment(payment("TAHSILAT", "100.00", null));
        Map<String, Object> first = payment("IADE", "60.00", chargeId);
        Map<String, Object> second = payment("IADE", "60.00", chargeId);

        List<Throwable> failures = runConcurrently(() -> data.insertPayment(first), () -> data.insertPayment(second));

        assertExactlyOneRejectedWith("EOD-004", failures);
        assertThat(paymentCount("IADE")).isEqualTo(1);
    }

    private Map<String, Object> payment(String type, String amount, Long sourceTransactionId) {
        Map<String, Object> row = data.paymentRow(orderId);
        row.put("islem_turu", type);
        row.put("tutar", new BigDecimal(amount));
        row.put("kaynak_islem_id", sourceTransactionId);
        return row;
    }

    private long paymentCount(String type) {
        return data.count(
                "SELECT COUNT(*) FROM ODEME_ISLEMI WHERE siparis_id = :id AND islem_turu = :tur AND durum = 'BASARILI'",
                Map.of("id", orderId, "tur", type));
    }

    /**
     * Runs both statements on separate threads, releasing them at the same moment, and returns each one's failure
     * ({@code null} for a statement that succeeded).
     */
    private static List<Throwable> runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Callable<?> statement : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        statement.call();
                        return null;
                    } catch (Exception failure) {
                        return failure;
                    }
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                failures.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertExactlyOneRejectedWith(String ruleCode, List<Throwable> failures) {
        List<Throwable> rejected = failures.stream().filter(Objects::nonNull).toList();
        assertThat(rejected).hasSize(1);
        assertIsRuleViolation(ruleCode, rejected.getFirst());
    }
}
