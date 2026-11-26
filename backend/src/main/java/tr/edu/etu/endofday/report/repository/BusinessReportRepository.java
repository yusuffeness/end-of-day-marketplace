package tr.edu.etu.endofday.report.repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.report.ReportRange;
import tr.edu.etu.endofday.report.SalesSummary;

/** The sales report of one business, read by the application account from the base tables. */
@Repository
public class BusinessReportRepository {

    private static final String ORDERS_IN_RANGE = """
            s.isletme_id = :businessId
            AND s.olusturulma_zamani >= :start
            AND s.olusturulma_zamani < :end
            """;

    private final JdbcClient jdbcClient;

    public BusinessReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public SalesSummary summarize(long businessId, ReportRange range) {
        Map<String, Object> counts =
                query("""
                        SELECT COUNT(*) AS siparis_sayisi,
                               COALESCE(SUM(s.durum = 'TESLIM_EDILDI'), 0) AS teslim_sayisi,
                               COALESCE(SUM(s.durum = 'IPTAL_EDILDI'), 0) AS iptal_sayisi,
                               COALESCE(SUM(s.durum = 'TESLIM_ALINMADI'), 0) AS alinmayan_sayisi
                        FROM SIPARIS s
                        WHERE
                        """ + ORDERS_IN_RANGE, businessId, range).query().singleRow();
        long units = query("""
                        SELECT COALESCE(SUM(k.adet), 0)
                        FROM SIPARIS_KALEMI k
                        JOIN SIPARIS s ON s.siparis_id = k.siparis_id
                        WHERE s.durum <> 'IPTAL_EDILDI' AND
                        """ + ORDERS_IN_RANGE, businessId, range)
                .query(Long.class)
                .single();
        Map<String, Object> payments =
                query("""
                        SELECT COALESCE(SUM(CASE WHEN o.islem_turu = 'TAHSILAT' THEN o.tutar END), 0) AS tahsilat,
                               COALESCE(SUM(CASE WHEN o.islem_turu = 'IADE' THEN o.tutar END), 0) AS iade
                        FROM ODEME_ISLEMI o
                        JOIN SIPARIS s ON s.siparis_id = o.siparis_id
                        WHERE o.durum = 'BASARILI' AND
                        """ + ORDERS_IN_RANGE, businessId, range).query().singleRow();
        Map<Integer, Long> ratings = new HashMap<>();
        query("""
                        SELECT d.puan, COUNT(*) AS adet
                        FROM DEGERLENDIRME d
                        JOIN SIPARIS s ON s.siparis_id = d.siparis_id
                        WHERE
                        """ + ORDERS_IN_RANGE + " GROUP BY d.puan", businessId, range).query(rs -> {
            ratings.put(rs.getInt("puan"), rs.getLong("adet"));
        });
        return new SalesSummary(
                number(counts, "siparis_sayisi"),
                number(counts, "teslim_sayisi"),
                number(counts, "iptal_sayisi"),
                number(counts, "alinmayan_sayisi"),
                units,
                (BigDecimal) payments.get("tahsilat"),
                (BigDecimal) payments.get("iade"),
                ratings);
    }

    private JdbcClient.StatementSpec query(String sql, long businessId, ReportRange range) {
        return jdbcClient
                .sql(sql)
                .param("businessId", businessId)
                .param("start", range.start())
                .param("end", range.endExclusive());
    }

    private static long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }
}
