package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.edu.etu.endofday.schema.SchemaTestData.BASE_TIME;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.support.IntegrationTest;

/**
 * Proves the values and the shape of the reporting views of V4__create_report_views.sql.
 *
 * <p>The data set belongs to one business and is small enough to be checked by hand:
 *
 * <ul>
 *   <li>Order 1, March, ONAYLANDI: 2 x 30.00 and 1 x 45.50 = 105.50. Successful charge 105.50, successful refund 30.00,
 *       failed refund 10.00 and a failed charge, so 105.50 - 30.00 = 75.50 is kept.
 *   <li>Order 2, March, IPTAL_EDILDI: 3 x 20.00 = 60.00, charged and fully refunded, so 0.00 is kept.
 *   <li>Order 3, March, ONAYLANDI: no items and no payments.
 *   <li>Order 4, April, TESLIM_EDILDI: 4 x 10.00 = 40.00, no payments.
 * </ul>
 *
 * <p>March therefore has 3 orders, 1 of them cancelled, and 3 units sold (the 3 units of the cancelled order are not
 * counted); April has 1 order and 4 units sold. The views are read as eod_app inside the test transaction, which is
 * rolled back afterwards.
 */
@Transactional
class ReportViewsIntegrationTest extends IntegrationTest {

    private static final List<String> VIEWS = List.of(
            "v_order_summary",
            "v_business_sales",
            "v_payment_summary",
            "v_review_summary",
            "v_business_status",
            "v_user_registrations");

    private static final List<String> PERSONAL_COLUMNS =
            List.of("ad", "soyad", "eposta", "telefon", "parola_ozeti", "adres", "yorum", "kullanici_id");

    private static final LocalDateTime MARCH = BASE_TIME.minusHours(1);
    private static final LocalDateTime APRIL = LocalDateTime.of(2026, 4, 10, 12, 0);

    @Autowired
    private JdbcClient jdbcClient;

    private SchemaTestData data;
    private long businessId;
    private String businessName;
    private long order1;
    private long order2;
    private long order3;
    private long order4;

    @BeforeEach
    void createDataSet() {
        data = new SchemaTestData(jdbcClient);
        Map<String, Object> business = data.businessRow(data.createUser());
        business.put("isletme_turu", "KAFE");
        business.put("sehir", "İzmir");
        business.put("ilce", "Karşıyaka");
        businessId = data.insertBusiness(business);
        businessName = (String) business.get("isletme_adi");
        long customerId = data.createUser();
        long listing1 = data.createListing(businessId);
        long listing2 = data.createListing(businessId);

        order1 = order(customerId, "ONAYLANDI", MARCH);
        item(order1, listing1, 2, "30.00");
        item(order1, listing2, 1, "45.50");
        long charge1 = payment(order1, "TAHSILAT", "BASARILI", "105.50", null);
        payment(order1, "TAHSILAT", "BASARISIZ", "105.50", null);
        payment(order1, "IADE", "BASARILI", "30.00", charge1);
        payment(order1, "IADE", "BASARISIZ", "10.00", charge1);

        order2 = order(customerId, "IPTAL_EDILDI", MARCH);
        item(order2, listing1, 3, "20.00");
        long charge2 = payment(order2, "TAHSILAT", "BASARILI", "60.00", null);
        payment(order2, "IADE", "BASARILI", "60.00", charge2);

        order3 = order(customerId, "ONAYLANDI", MARCH);

        order4 = order(customerId, "TESLIM_EDILDI", APRIL);
        item(order4, listing2, 4, "10.00");
    }

    @Test
    void orderSummaryCountsItemsAndTotalsEveryOrder() {
        List<OrderSummary> rows = jdbcClient
                .sql("SELECT * FROM v_order_summary WHERE isletme_id = :id ORDER BY siparis_id")
                .param("id", businessId)
                .query((rs, n) -> new OrderSummary(
                        rs.getLong("siparis_id"),
                        rs.getLong("isletme_id"),
                        rs.getString("isletme_turu"),
                        rs.getString("sehir"),
                        rs.getString("ilce"),
                        rs.getObject("olusturulma_zamani", LocalDateTime.class),
                        rs.getString("durum"),
                        rs.getLong("kalem_sayisi"),
                        rs.getBigDecimal("toplam_tutar")))
                .list();

        assertThat(rows)
                .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder()
                        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                        .build())
                .containsExactly(
                        orderSummary(order1, "ONAYLANDI", MARCH, 2, "105.50"),
                        orderSummary(order2, "IPTAL_EDILDI", MARCH, 1, "60.00"),
                        orderSummary(order3, "ONAYLANDI", MARCH, 0, "0.00"),
                        orderSummary(order4, "TESLIM_EDILDI", APRIL, 1, "40.00"));
    }

    @Test
    void businessSalesGroupsByMonthAndExcludesCancelledUnits() {
        List<BusinessSales> rows = jdbcClient
                .sql("SELECT * FROM v_business_sales WHERE isletme_id = :id ORDER BY ay")
                .param("id", businessId)
                .query((rs, n) -> new BusinessSales(
                        rs.getLong("isletme_id"),
                        rs.getString("isletme_adi"),
                        rs.getString("isletme_turu"),
                        rs.getString("sehir"),
                        rs.getString("ilce"),
                        rs.getString("ay"),
                        rs.getLong("siparis_sayisi"),
                        rs.getLong("iptal_sayisi"),
                        rs.getLong("satilan_adet")))
                .list();

        assertThat(rows)
                .containsExactly(
                        new BusinessSales(businessId, businessName, "KAFE", "İzmir", "Karşıyaka", "2026-03", 3, 1, 3),
                        new BusinessSales(businessId, businessName, "KAFE", "İzmir", "Karşıyaka", "2026-04", 1, 0, 4));
    }

    @Test
    void paymentSummaryNetsSuccessfulRefundsAgainstTheSuccessfulCharge() {
        Map<Long, List<BigDecimal>> amounts = new LinkedHashMap<>();
        jdbcClient
                .sql("SELECT * FROM v_payment_summary WHERE isletme_id = :id ORDER BY siparis_id")
                .param("id", businessId)
                .query(rs -> {
                    amounts.put(
                            rs.getLong("siparis_id"),
                            List.of(
                                    rs.getBigDecimal("tahsilat_tutari"),
                                    rs.getBigDecimal("iade_tutari"),
                                    rs.getBigDecimal("net_tutar")));
                });

        assertThat(amounts).containsOnlyKeys(order1, order2, order3, order4);
        assertAmounts(amounts.get(order1), "105.50", "30.00", "75.50");
        assertAmounts(amounts.get(order2), "60.00", "60.00", "0.00");
        assertAmounts(amounts.get(order3), "0.00", "0.00", "0.00");
        assertAmounts(amounts.get(order4), "0.00", "0.00", "0.00");
    }

    @Test
    void orderSummaryAddsUpTheItemQuantities() {
        Map<Long, Long> units = new LinkedHashMap<>();
        jdbcClient
                .sql("SELECT siparis_id, adet FROM v_order_summary WHERE isletme_id = :id")
                .param("id", businessId)
                .query(rs -> {
                    units.put(rs.getLong("siparis_id"), rs.getLong("adet"));
                });

        assertThat(units).containsExactlyInAnyOrderEntriesOf(Map.of(order1, 3L, order2, 3L, order3, 0L, order4, 4L));
    }

    @Test
    void schemaContainsExactlyTheReportViews() {
        List<String> views = jdbcClient
                .sql("SELECT TABLE_NAME FROM information_schema.VIEWS WHERE TABLE_SCHEMA = 'end_of_day'")
                .query(String.class)
                .list();

        assertThat(views).containsExactlyInAnyOrderElementsOf(VIEWS);
    }

    @Test
    void viewsHaveExactlyTheDocumentedColumns() {
        assertThat(viewColumns("v_order_summary"))
                .containsExactly(
                        "siparis_id",
                        "isletme_id",
                        "isletme_turu",
                        "sehir",
                        "ilce",
                        "olusturulma_zamani",
                        "durum",
                        "kalem_sayisi",
                        "toplam_tutar",
                        "adet");
        assertThat(viewColumns("v_review_summary"))
                .containsExactly("siparis_id", "isletme_id", "siparis_zamani", "puan", "degerlendirme_zamani");
        assertThat(viewColumns("v_business_status"))
                .containsExactly("isletme_id", "isletme_turu", "sehir", "ilce", "onay_durumu", "aktif_mi");
        assertThat(viewColumns("v_user_registrations")).containsExactly("kayit_tarihi", "aktif_mi");
        assertThat(viewColumns("v_business_sales"))
                .containsExactly(
                        "isletme_id",
                        "isletme_adi",
                        "isletme_turu",
                        "sehir",
                        "ilce",
                        "ay",
                        "siparis_sayisi",
                        "iptal_sayisi",
                        "satilan_adet");
        assertThat(viewColumns("v_payment_summary"))
                .containsExactly("siparis_id", "isletme_id", "tahsilat_tutari", "iade_tutari", "net_tutar");
    }

    @Test
    void viewsExposeNoPersonalColumns() {
        for (String view : VIEWS) {
            assertThat(viewColumns(view)).as(view).doesNotContainAnyElementsOf(PERSONAL_COLUMNS);
        }
    }

    private List<String> viewColumns(String view) {
        return jdbcClient
                .sql("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = 'end_of_day' AND TABLE_NAME = :view ORDER BY ORDINAL_POSITION")
                .param("view", view)
                .query(String.class)
                .list();
    }

    private long order(long customerId, String status, LocalDateTime createdAt) {
        Map<String, Object> row = data.orderRow(customerId, businessId);
        row.put("olusturulma_zamani", createdAt);
        row.put("durum", status);
        row.put("iptal_zamani", "IPTAL_EDILDI".equals(status) ? createdAt.plusMinutes(10) : null);
        return data.insertOrder(row);
    }

    private void item(long orderId, long listingId, int quantity, String unitPrice) {
        Map<String, Object> row = data.orderItemRow(orderId, listingId);
        row.put("adet", quantity);
        row.put("birim_fiyat", new BigDecimal(unitPrice));
        data.insertOrderItem(row);
    }

    private long payment(long orderId, String type, String status, String amount, Long sourceTransactionId) {
        Map<String, Object> row = data.paymentRow(orderId);
        row.put("islem_turu", type);
        row.put("durum", status);
        row.put("tutar", new BigDecimal(amount));
        row.put("kaynak_islem_id", sourceTransactionId);
        return data.insertPayment(row);
    }

    private OrderSummary orderSummary(
            long orderId, String status, LocalDateTime createdAt, long itemCount, String total) {
        return new OrderSummary(
                orderId, businessId, "KAFE", "İzmir", "Karşıyaka", createdAt, status, itemCount, new BigDecimal(total));
    }

    private static void assertAmounts(List<BigDecimal> actual, String charged, String refunded, String net) {
        assertThat(actual.get(0)).as("tahsilat_tutari").isEqualByComparingTo(charged);
        assertThat(actual.get(1)).as("iade_tutari").isEqualByComparingTo(refunded);
        assertThat(actual.get(2)).as("net_tutar").isEqualByComparingTo(net);
    }

    private record OrderSummary(
            long siparisId,
            long isletmeId,
            String isletmeTuru,
            String sehir,
            String ilce,
            LocalDateTime olusturulmaZamani,
            String durum,
            long kalemSayisi,
            BigDecimal toplamTutar) {}

    private record BusinessSales(
            long isletmeId,
            String isletmeAdi,
            String isletmeTuru,
            String sehir,
            String ilce,
            String ay,
            long siparisSayisi,
            long iptalSayisi,
            long satilanAdet) {}
}
