package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.edu.etu.endofday.schema.DemoData.withDemoData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tr.edu.etu.endofday.report.ReportRange;
import tr.edu.etu.endofday.report.SalesSummary;
import tr.edu.etu.endofday.report.repository.BusinessReportRepository;
import tr.edu.etu.endofday.report.repository.PlatformReportRepository;
import tr.edu.etu.endofday.support.IntegrationTest;

/**
 * The business and platform report queries against the demo data, compared with values computed by hand from
 * db/seed/R__seed_demo_data.sql. The demo data is loaded in a transaction that is rolled back afterwards.
 *
 * <p>Karaca Fırını Kızılay Şubesi has eight historical orders:
 *
 * <ul>
 *   <li>September 2026: O1 delivered, 3 units, 110.00, rated 5; O2 delivered, 1 unit, 35.00; O3 not picked up, 1 unit,
 *       40.00; O4 cancelled, 1 unit, 35.00 charged and refunded.
 *   <li>October 2026: O5 delivered, 2 units, 120.00, rated 5; O6 delivered, 2 units, 140.00, rated 4; O7 and O8
 *       delivered, 1 unit and 70.00 each.
 * </ul>
 */
class ReportDemoDataIntegrationTest extends IntegrationTest {

    private static final ReportRange SEPTEMBER = new ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    private static final ReportRange OCTOBER = new ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    private static final ReportRange BOTH = new ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

    @Test
    void septemberExcludesTheCancelledUnitsAndNetsTheRefund() {
        withDemoData(db -> {
            SalesSummary report = new BusinessReportRepository(db).summarize(kizilay(db), SEPTEMBER);

            assertThat(report.orderCount()).isEqualTo(4);
            assertThat(report.deliveredCount()).isEqualTo(2);
            assertThat(report.cancelledCount()).isEqualTo(1);
            assertThat(report.notPickedUpCount()).isEqualTo(1);
            // O1 3 + O2 1 + O3 1; the unit of the cancelled O4 is not counted, the not picked up O3 is.
            assertThat(report.unitsSold()).isEqualTo(5);
            assertThat(report.grossRevenue()).isEqualByComparingTo("220.00");
            assertThat(report.refundedAmount()).isEqualByComparingTo("35.00");
            assertThat(report.netRevenue()).isEqualByComparingTo("185.00");
            assertThat(report.ratingDistribution())
                    .containsExactly(
                            Map.entry(1, 0L), Map.entry(2, 0L), Map.entry(3, 0L), Map.entry(4, 0L), Map.entry(5, 1L));
            assertThat(report.averageRating()).isEqualByComparingTo("5.00");
        });
    }

    @Test
    void octoberHasOnlyDeliveredOrders() {
        withDemoData(db -> {
            SalesSummary report = new BusinessReportRepository(db).summarize(kizilay(db), OCTOBER);

            assertThat(report.orderCount()).isEqualTo(4);
            assertThat(report.deliveredCount()).isEqualTo(4);
            assertThat(report.cancelledCount()).isZero();
            assertThat(report.unitsSold()).isEqualTo(6);
            assertThat(report.grossRevenue()).isEqualByComparingTo("400.00");
            assertThat(report.refundedAmount()).isEqualByComparingTo("0.00");
            assertThat(report.netRevenue()).isEqualByComparingTo("400.00");
            assertThat(report.averageRating()).isEqualByComparingTo("4.50");
        });
    }

    @Test
    void twoMonthsAddUp() {
        withDemoData(db -> {
            SalesSummary report = new BusinessReportRepository(db).summarize(kizilay(db), BOTH);

            assertThat(report.orderCount()).isEqualTo(8);
            assertThat(report.unitsSold()).isEqualTo(11);
            assertThat(report.netRevenue()).isEqualByComparingTo("585.00");
            assertThat(report.ratingCount()).isEqualTo(3);
            // (5 + 5 + 4) / 3 = 4.666...
            assertThat(report.averageRating()).isEqualByComparingTo("4.67");
        });
    }

    @Test
    void platformReportFromTheViewsMatchesTheSumOfAllBusinessReports() {
        withDemoData(db -> {
            BusinessReportRepository businessReports = new BusinessReportRepository(db);
            List<Long> businesses =
                    db.sql("SELECT isletme_id FROM ISLETME").query(Long.class).list();
            long orders = 0;
            long units = 0;
            long cancelled = 0;
            BigDecimal net = BigDecimal.ZERO;
            long ratings = 0;
            for (long business : businesses) {
                SalesSummary report = businessReports.summarize(business, BOTH);
                orders += report.orderCount();
                units += report.unitsSold();
                cancelled += report.cancelledCount();
                net = net.add(report.netRevenue());
                ratings += report.ratingCount();
            }

            SalesSummary platform = new PlatformReportRepository(db).summarize(BOTH);

            assertThat(platform.orderCount()).isEqualTo(orders).isPositive();
            assertThat(platform.unitsSold()).isEqualTo(units);
            assertThat(platform.cancelledCount()).isEqualTo(cancelled);
            assertThat(platform.netRevenue()).isEqualByComparingTo(net);
            assertThat(platform.ratingCount()).isEqualTo(ratings);
        });
    }

    @Test
    void platformCountsActiveBusinessesAndNewUsersFromTheViews() {
        withDemoData(db -> {
            PlatformReportRepository platform = new PlatformReportRepository(db);

            // Six approved businesses; Koç Simit Fırını is still pending.
            assertThat(platform.countActiveBusinesses()).isEqualTo(6);
            // Ayten Koç registered on 2026-10-28; everyone else in August 2026.
            assertThat(platform.countNewUsers(OCTOBER)).isEqualTo(1);
            assertThat(platform.countNewUsers(new ReportRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))))
                    .isEqualTo(18);
        });
    }

    private static long kizilay(JdbcClient db) {
        return db.sql("SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'")
                .query(Long.class)
                .single();
    }
}
