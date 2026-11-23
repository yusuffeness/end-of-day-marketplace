package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.OwnerJdbc;

/**
 * Proves proc_mark_not_picked_up and its schedule evt_mark_not_picked_up (V3__create_not_picked_up_event.sql).
 *
 * <p>The procedure compares pickup windows with the database clock, so the windows are placed relative to the
 * application clock, which runs in the same time zone. The tests call the procedure as eod_app inside a transaction
 * that is rolled back afterwards; the scheduled event never sees this uncommitted data.
 */
@Transactional
class NotPickedUpProcedureIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private Clock clock;

    private SchemaTestData data;
    private long businessId;
    private long listingId;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        data = new SchemaTestData(jdbcClient);
        businessId = data.createApprovedBusiness();
        listingId = data.createListing(businessId);
        now = LocalDateTime.now(clock);
    }

    @Test
    void marksConfirmedOrderWhosePickupWindowHasEndedAsNotPickedUp() {
        long orderId = orderWithPickupWindowEnding("ONAYLANDI", now.minusHours(1));

        markNotPickedUp();

        assertThat(orderStatus(orderId)).isEqualTo("TESLIM_ALINMADI");
    }

    @Test
    void leavesStockAndPaymentsUntouchedWhenMarkingOrderAsNotPickedUp() {
        long orderId = orderWithPickupWindowEnding("ONAYLANDI", now.minusHours(1));
        data.insertOrderItem(data.orderItemRow(orderId, listingId));
        data.createCharge(orderId);
        long remainingBefore = remainingQuantity();
        long paymentsBefore = paymentCount(orderId);

        markNotPickedUp();

        assertThat(orderStatus(orderId)).isEqualTo("TESLIM_ALINMADI");
        assertThat(remainingQuantity()).isEqualTo(remainingBefore);
        assertThat(paymentCount(orderId)).isEqualTo(paymentsBefore);
    }

    @Test
    void leavesConfirmedOrderWhosePickupWindowHasNotEndedUnchanged() {
        long orderId = orderWithPickupWindowEnding("ONAYLANDI", now.plusDays(1));

        markNotPickedUp();

        assertThat(orderStatus(orderId)).isEqualTo("ONAYLANDI");
    }

    @Test
    void leavesDeliveredAndCancelledOrdersUnchanged() {
        long deliveredId = orderWithPickupWindowEnding("TESLIM_EDILDI", now.minusHours(1));
        long cancelledId = orderWithPickupWindowEnding("IPTAL_EDILDI", now.minusHours(1));

        markNotPickedUp();

        assertThat(orderStatus(deliveredId)).isEqualTo("TESLIM_EDILDI");
        assertThat(orderStatus(cancelledId)).isEqualTo("IPTAL_EDILDI");
    }

    @Test
    void eventRunsTheProcedureEveryFiveMinutes() {
        // information_schema.EVENTS lists only the events of schemas on which the reader holds the EVENT privilege,
        // which eod_app does not have; the schema owner reads it instead.
        Map<String, Object> event = OwnerJdbc.client()
                .sql("SELECT STATUS, INTERVAL_VALUE, INTERVAL_FIELD, ON_COMPLETION, EVENT_DEFINITION"
                        + " FROM information_schema.EVENTS"
                        + " WHERE EVENT_SCHEMA = 'end_of_day' AND EVENT_NAME = 'evt_mark_not_picked_up'")
                .query()
                .singleRow();

        assertThat(event)
                .containsEntry("STATUS", "ENABLED")
                .containsEntry("INTERVAL_VALUE", "5")
                .containsEntry("INTERVAL_FIELD", "MINUTE")
                .containsEntry("ON_COMPLETION", "PRESERVE");
        assertThat((String) event.get("EVENT_DEFINITION")).containsIgnoringCase("CALL proc_mark_not_picked_up()");
    }

    private long orderWithPickupWindowEnding(String status, LocalDateTime pickupEnd) {
        Map<String, Object> order = data.orderRow(data.createUser(), businessId);
        order.put("olusturulma_zamani", pickupEnd.minusHours(4));
        order.put("durum", status);
        order.put("iptal_zamani", "IPTAL_EDILDI".equals(status) ? pickupEnd.minusHours(3) : null);
        long orderId = data.insertOrder(order);

        Map<String, Object> pickup = data.pickupRow(orderId);
        pickup.put("teslim_baslangic", pickupEnd.minusHours(2));
        pickup.put("teslim_bitis", pickupEnd);
        if ("TESLIM_EDILDI".equals(status)) {
            pickup.put("teslim_zamani", pickupEnd.minusHours(1));
            pickup.put("dogrulayan_id", data.businessOwnerOfOrder(orderId));
        }
        data.insertPickup(pickup);
        return orderId;
    }

    private void markNotPickedUp() {
        jdbcClient.sql("CALL proc_mark_not_picked_up()").update();
    }

    private String orderStatus(long orderId) {
        return jdbcClient
                .sql("SELECT durum FROM SIPARIS WHERE siparis_id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private long remainingQuantity() {
        return data.count("SELECT kalan_adet FROM PAKET_ILANI WHERE ilan_id = :id", Map.of("id", listingId));
    }

    private long paymentCount(long orderId) {
        return data.count("SELECT COUNT(*) FROM ODEME_ISLEMI WHERE siparis_id = :id", Map.of("id", orderId));
    }
}
