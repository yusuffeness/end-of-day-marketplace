package tr.edu.etu.endofday.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import tr.edu.etu.endofday.report.config.ReportDatabase;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/**
 * GET /admin/reports/platform and the eod_report connection of the application: the views can be read through it,
 * the base tables cannot.
 */
class PlatformReportIntegrationTest extends MarketplaceIntegrationTest {

    /** MySQL error 1142, ER_TABLEACCESS_DENIED_ERROR. */
    private static final int TABLE_ACCESS_DENIED = 1142;

    @Autowired
    private ReportDatabase reportDatabase;

    @Test
    void platformReportIsReadFromTheViews() throws Exception {
        Account admin = signUpAdmin("platform.yonetici");
        Account owner = signUpOwner("platform.sahip");
        Account customer = signUpCustomer("platform.musteri");
        long bakery = approvedBusiness(owner.id());
        long listing = listing(bakery, "120.00", "60.00", 10);
        long delivered = placedOrder(customer, bakery, listing, 2);
        long cancelled = placedOrder(customer, bakery, listing, 1);
        cancelOrder(customer, cancelled, newRequestId(), false).andExpect(status().isOk());
        clock.setTo(WINDOW_START.plusMinutes(1));
        verifyPickup(owner, bakery, pickupCodeOf(delivered)).andExpect(status().isOk());
        customer.client()
                .postJson("/api/v1/orders/" + delivered + "/review", "{\"rating\": 5}")
                .andExpect(status().isCreated());
        long activeBusinesses = db().sql("SELECT COUNT(*) FROM ISLETME WHERE onay_durumu = 'ONAYLANDI' AND aktif_mi")
                .query(Long.class)
                .single();
        long newUsers = db().sql("SELECT COUNT(*) FROM KULLANICI WHERE DATE(kayit_zamani) = '2031-03-10'")
                .query(Long.class)
                .single();

        admin.client()
                .perform(get("/api/v1/admin/reports/platform?from=2031-03-10&to=2031-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2031-03-10"))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.deliveredCount").value(1))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.unitsSold").value(2))
                .andExpect(jsonPath("$.grossRevenue").value("180.00"))
                .andExpect(jsonPath("$.refundedAmount").value("60.00"))
                .andExpect(jsonPath("$.netRevenue").value("120.00"))
                .andExpect(jsonPath("$.averageRating").value("5.00"))
                .andExpect(jsonPath("$.ratingDistribution.5").value(1))
                .andExpect(jsonPath("$.activeBusinessCount").value(activeBusinesses))
                .andExpect(jsonPath("$.newUserCount").value(newUsers));
        assertThat(newUsers).isEqualTo(3);
    }

    @Test
    void platformReportIsForAdministratorsOnly() throws Exception {
        Account owner = signUpOwner("platform.yetkisiz");

        owner.client()
                .perform(get("/api/v1/admin/reports/platform?from=2031-03-01&to=2031-03-31"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "v_order_summary",
                "v_business_sales",
                "v_payment_summary",
                "v_review_summary",
                "v_business_status",
                "v_user_registrations"
            })
    void reportConnectionReadsTheViews(String view) {
        Long rows = reportDatabase
                .jdbcClient()
                .sql("SELECT COUNT(*) FROM " + view)
                .query(Long.class)
                .single();

        assertThat(rows).isNotNegative();
    }

    @ParameterizedTest
    @ValueSource(strings = {"KULLANICI", "ISLETME", "SIPARIS", "ODEME_ISLEMI", "DEGERLENDIRME", "TESLIM_ALMA"})
    void reportConnectionCannotReadTheBaseTables(String table) {
        assertThatThrownBy(() -> reportDatabase
                        .jdbcClient()
                        .sql("SELECT COUNT(*) FROM " + table)
                        .query(Long.class)
                        .single())
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(TABLE_ACCESS_DENIED));
    }

    @Test
    void reportConnectionUsesTheReportAccount() {
        assertThat(reportDatabase
                        .jdbcClient()
                        .sql("SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1)")
                        .query(String.class)
                        .single())
                .isEqualTo("eod_report");
    }
}
