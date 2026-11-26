package tr.edu.etu.endofday.report.repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.report.ReportRange;
import tr.edu.etu.endofday.report.SalesSummary;
import tr.edu.etu.endofday.report.config.ReportDatabase;

/**
 * The platform report, read as eod_report from the reporting views only (V4, V6). This repository has no access to the
 * application's connection: every query goes through the read-only eod_report pool.
 */
@Repository
public class PlatformReportRepository {

    private static final String ORDERS_IN_RANGE = "olusturulma_zamani >= :start AND olusturulma_zamani < :end";

    private final JdbcClient jdbcClient;

    @Autowired
    public PlatformReportRepository(ReportDatabase reportDatabase) {
        this(reportDatabase.jdbcClient());
    }

    /** Reads the same views through another connection, for example one that sees uncommitted test data. */
    public PlatformReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public SalesSummary summarize(ReportRange range) {
        Map<String, Object> orders =
                inRange("""
                        SELECT COUNT(*) AS siparis_sayisi,
                               COALESCE(SUM(durum = 'TESLIM_EDILDI'), 0) AS teslim_sayisi,
                               COALESCE(SUM(durum = 'IPTAL_EDILDI'), 0) AS iptal_sayisi,
                               COALESCE(SUM(durum = 'TESLIM_ALINMADI'), 0) AS alinmayan_sayisi,
                               COALESCE(SUM(CASE WHEN durum <> 'IPTAL_EDILDI' THEN adet ELSE 0 END), 0) AS satilan_adet
                        FROM v_order_summary
                        WHERE
                        """ + ORDERS_IN_RANGE, range).query().singleRow();
        Map<String, Object> payments = inRange("""
                        SELECT COALESCE(SUM(p.tahsilat_tutari), 0) AS tahsilat,
                               COALESCE(SUM(p.iade_tutari), 0) AS iade
                        FROM v_payment_summary p
                        JOIN v_order_summary o ON o.siparis_id = p.siparis_id
                        WHERE o.olusturulma_zamani >= :start AND o.olusturulma_zamani < :end
                        """, range).query().singleRow();
        Map<Integer, Long> ratings = new HashMap<>();
        inRange("""
                        SELECT puan, COUNT(*) AS adet
                        FROM v_review_summary
                        WHERE siparis_zamani >= :start AND siparis_zamani < :end
                        GROUP BY puan
                        """, range).query(rs -> {
            ratings.put(rs.getInt("puan"), rs.getLong("adet"));
        });
        return new SalesSummary(
                number(orders, "siparis_sayisi"),
                number(orders, "teslim_sayisi"),
                number(orders, "iptal_sayisi"),
                number(orders, "alinmayan_sayisi"),
                number(orders, "satilan_adet"),
                (BigDecimal) payments.get("tahsilat"),
                (BigDecimal) payments.get("iade"),
                ratings);
    }

    /** Approved and active businesses at the time of the report. */
    public long countActiveBusinesses() {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM v_business_status WHERE onay_durumu = 'ONAYLANDI' AND aktif_mi")
                .query(Long.class)
                .single();
    }

    /** Accounts registered within the range. */
    public long countNewUsers(ReportRange range) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM v_user_registrations WHERE kayit_tarihi BETWEEN :from AND :to")
                .param("from", range.from())
                .param("to", range.to())
                .query(Long.class)
                .single();
    }

    private JdbcClient.StatementSpec inRange(String sql, ReportRange range) {
        return jdbcClient.sql(sql).param("start", range.start()).param("end", range.endExclusive());
    }

    private static long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }
}
