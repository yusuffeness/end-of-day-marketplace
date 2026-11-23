package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static tr.edu.etu.endofday.schema.DemoData.withDemoData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.OwnerJdbc;

/**
 * Proves that the dev demo data (db/seed/R__seed_demo_data.sql) loads through every constraint and trigger, is
 * internally consistent and contains the example of report section 6.1 exactly.
 *
 * <p>Each test loads the file in one transaction on an eod_owner connection and rolls it back (see {@link DemoData}).
 */
class SeedDataIntegrationTest extends IntegrationTest {

    private static final String DEMO_PASSWORD = "GunSonu2026!";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loadsDemoDataInOneTransactionThatRollsBackWithoutTrace() {
        Map<String, Long> before = DemoData.rowCounts(OwnerJdbc.client());

        withDemoData(db -> assertThat(DemoData.rowCounts(db))
                .containsExactly(
                        entry("KULLANICI", 19L),
                        entry("ROL", 3L),
                        entry("KULLANICI_ROL", 25L),
                        entry("ISLETME", 7L),
                        entry("PAKET_ILANI", 22L),
                        entry("SIPARIS", 32L),
                        entry("SIPARIS_KALEMI", 33L),
                        entry("ODEME_ISLEMI", 35L),
                        entry("TESLIM_ALMA", 32L),
                        entry("DEGERLENDIRME", 10L)));

        // An implicit commit inside the file would have made the demo rows permanent.
        assertThat(DemoData.rowCounts(OwnerJdbc.client())).isEqualTo(before);
    }

    static Stream<Arguments> consistencyChecks() {
        return Stream.of(
                Arguments.of(
                        "kalan_adet differs from toplam_adet minus the units of orders that were not cancelled", """
                        SELECT p.ilan_id
                        FROM PAKET_ILANI p
                        LEFT JOIN (SELECT k.ilan_id, SUM(k.adet) AS adet
                                   FROM SIPARIS_KALEMI k
                                   JOIN SIPARIS s ON s.siparis_id = k.siparis_id
                                   WHERE s.durum <> 'IPTAL_EDILDI'
                                   GROUP BY k.ilan_id) x ON x.ilan_id = p.ilan_id
                        WHERE p.kalan_adet <> p.toplam_adet - COALESCE(x.adet, 0)
                        """),
                Arguments.of("order without exactly one successful charge", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        LEFT JOIN ODEME_ISLEMI o ON o.siparis_id = s.siparis_id
                            AND o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI'
                        GROUP BY s.siparis_id
                        HAVING COUNT(o.islem_id) <> 1
                        """),
                Arguments.of("successful charge that differs from the item total", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        JOIN ODEME_ISLEMI o ON o.siparis_id = s.siparis_id
                            AND o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI'
                        WHERE o.tutar <> (SELECT SUM(k.adet * k.birim_fiyat)
                                          FROM SIPARIS_KALEMI k
                                          WHERE k.siparis_id = s.siparis_id)
                        """),
                Arguments.of("cancelled order that is not fully refunded against its charge", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        JOIN ODEME_ISLEMI c ON c.siparis_id = s.siparis_id
                            AND c.islem_turu = 'TAHSILAT' AND c.durum = 'BASARILI'
                        WHERE s.durum = 'IPTAL_EDILDI'
                          AND c.tutar <> (SELECT COALESCE(SUM(r.tutar), 0)
                                          FROM ODEME_ISLEMI r
                                          WHERE r.kaynak_islem_id = c.islem_id
                                            AND r.islem_turu = 'IADE' AND r.durum = 'BASARILI')
                        """),
                Arguments.of("refund on an order that was not cancelled", """
                        SELECT o.siparis_id
                        FROM ODEME_ISLEMI o
                        JOIN SIPARIS s ON s.siparis_id = o.siparis_id
                        WHERE o.islem_turu = 'IADE' AND s.durum <> 'IPTAL_EDILDI'
                        """),
                Arguments.of("order without a TESLIM_ALMA row", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        LEFT JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
                        WHERE t.siparis_id IS NULL
                        """),
                Arguments.of("delivered order not verified by the business owner within the pickup window", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        JOIN ISLETME i ON i.isletme_id = s.isletme_id
                        JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
                        WHERE s.durum = 'TESLIM_EDILDI'
                          AND (t.dogrulayan_id IS NULL
                               OR t.dogrulayan_id <> i.yetkili_id
                               OR t.teslim_zamani NOT BETWEEN t.teslim_baslangic AND t.teslim_bitis)
                        """),
                Arguments.of("order that was not delivered but has a pickup time", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
                        WHERE s.durum <> 'TESLIM_EDILDI' AND t.teslim_zamani IS NOT NULL
                        """),
                Arguments.of("order without items", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        WHERE NOT EXISTS (SELECT 1 FROM SIPARIS_KALEMI k WHERE k.siparis_id = s.siparis_id)
                        """),
                Arguments.of(
                        "order placed after its pickup window opened or with a window other than its listing's", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
                        JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
                        JOIN PAKET_ILANI p ON p.ilan_id = k.ilan_id
                        WHERE s.olusturulma_zamani >= t.teslim_baslangic
                           OR t.teslim_baslangic <> p.teslim_baslangic
                           OR t.teslim_bitis <> p.teslim_bitis
                        """),
                Arguments.of("historical listing whose pickup window leaves its own day", """
                        SELECT p.ilan_id
                        FROM PAKET_ILANI p
                        WHERE p.durum = 'PASIF'
                          AND (DATE(p.teslim_baslangic) <> DATE(p.teslim_bitis)
                               OR DATE(p.teslim_baslangic) <> DATE(p.olusturulma_zamani))
                        """),
                Arguments.of("cancellation time not before the pickup window", """
                        SELECT s.siparis_id
                        FROM SIPARIS s
                        JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
                        WHERE s.iptal_zamani IS NOT NULL AND s.iptal_zamani >= t.teslim_baslangic
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("consistencyChecks")
    void consistencyCheckFindsNoRows(String description, String query) {
        withDemoData(db ->
                assertThat(db.sql(query).query().listOfRows()).as(description).isEmpty());
    }

    @Test
    void containsTheExampleOfReportSection61Exactly() {
        withDemoData(db -> {
            Map<String, Object> listing = db.sql("""
                            SELECT p.ilan_id, p.toplam_adet, p.kalan_adet, p.normal_fiyat, p.satis_fiyati,
                                   p.teslim_baslangic, p.teslim_bitis, i.isletme_turu, i.yetkili_id
                            FROM PAKET_ILANI p
                            JOIN ISLETME i ON i.isletme_id = p.isletme_id
                            WHERE p.baslik = 'Karışık Unlu Mamul Paketi'
                              AND i.isletme_adi = 'Karaca Fırını Kızılay Şubesi'
                            """).query().singleRow();
            assertThat(listing.get("isletme_turu")).isEqualTo("FIRIN");
            assertThat(((Number) listing.get("toplam_adet")).intValue()).isEqualTo(5);
            assertThat(((Number) listing.get("kalan_adet")).intValue()).isEqualTo(3);
            assertThat((BigDecimal) listing.get("normal_fiyat")).isEqualByComparingTo("120.00");
            assertThat((BigDecimal) listing.get("satis_fiyati")).isEqualByComparingTo("60.00");
            assertThat(listing.get("teslim_baslangic")).isEqualTo(LocalDateTime.of(2026, 10, 15, 19, 0));
            assertThat(listing.get("teslim_bitis")).isEqualTo(LocalDateTime.of(2026, 10, 15, 20, 0));

            Map<String, Object> order =
                    db.sql("""
                            SELECT s.siparis_id, s.durum, k.adet, k.birim_fiyat
                            FROM SIPARIS_KALEMI k
                            JOIN SIPARIS s ON s.siparis_id = k.siparis_id
                            WHERE k.ilan_id = :ilan
                            """).param("ilan", listing.get("ilan_id")).query().singleRow();
            Object orderId = order.get("siparis_id");
            assertThat(order.get("durum")).isEqualTo("TESLIM_EDILDI");
            assertThat(((Number) order.get("adet")).intValue()).isEqualTo(2);
            assertThat((BigDecimal) order.get("birim_fiyat")).isEqualByComparingTo("60.00");

            List<Map<String, Object>> payments = db.sql(
                            "SELECT islem_turu, durum, tutar FROM ODEME_ISLEMI WHERE siparis_id = :siparis")
                    .param("siparis", orderId)
                    .query()
                    .listOfRows();
            assertThat(payments).hasSize(1);
            assertThat(payments.getFirst())
                    .containsEntry("islem_turu", "TAHSILAT")
                    .containsEntry("durum", "BASARILI");
            assertThat((BigDecimal) payments.getFirst().get("tutar")).isEqualByComparingTo("120.00");

            Map<String, Object> pickup = db.sql("SELECT * FROM TESLIM_ALMA WHERE siparis_id = :siparis")
                    .param("siparis", orderId)
                    .query()
                    .singleRow();
            assertThat((String) pickup.get("teslim_kodu")).matches("[0-9]{6}");
            assertThat(pickup.get("teslim_baslangic")).isEqualTo(LocalDateTime.of(2026, 10, 15, 19, 0));
            assertThat(pickup.get("teslim_bitis")).isEqualTo(LocalDateTime.of(2026, 10, 15, 20, 0));
            assertThat((LocalDateTime) pickup.get("teslim_zamani"))
                    .isBetween(LocalDateTime.of(2026, 10, 15, 19, 0), LocalDateTime.of(2026, 10, 15, 20, 0));
            assertThat(pickup.get("dogrulayan_id")).isEqualTo(listing.get("yetkili_id"));

            Integer rating = db.sql("SELECT puan FROM DEGERLENDIRME WHERE siparis_id = :siparis")
                    .param("siparis", orderId)
                    .query(Integer.class)
                    .single();
            assertThat(rating).isBetween(1, 5);
        });
    }

    @Test
    void everyDemoAccountUsesTheDocumentedPassword() {
        withDemoData(db -> {
            List<String> hashes = db.sql("SELECT parola_ozeti FROM KULLANICI WHERE eposta LIKE '%@ornek.test'")
                    .query(String.class)
                    .list();

            assertThat(hashes)
                    .hasSize(19)
                    .allSatisfy(hash -> assertThat(passwordEncoder.matches(DEMO_PASSWORD, hash))
                            .isTrue());
        });
    }

    @Test
    void demoAccountsCoverTheRoleCombinationsOfTheReport() {
        withDemoData(db -> {
            // Every account is a customer.
            assertThat(db.sql("""
                            SELECT COUNT(*) FROM KULLANICI k
                            WHERE NOT EXISTS (SELECT 1 FROM KULLANICI_ROL kr JOIN ROL r ON r.rol_id = kr.rol_id
                                              WHERE kr.kullanici_id = k.kullanici_id AND r.rol_adi = 'MUSTERI')
                            """).query(Long.class).single()).isZero();
            // One owner runs two branches.
            assertThat(db.sql("SELECT MAX(n) FROM (SELECT COUNT(*) AS n FROM ISLETME GROUP BY yetkili_id) x")
                            .query(Long.class)
                            .single())
                    .isEqualTo(2);
            // The owner of the pending business has no owner role yet.
            assertThat(db.sql("""
                            SELECT r.rol_adi FROM ISLETME i
                            JOIN KULLANICI_ROL kr ON kr.kullanici_id = i.yetkili_id
                            JOIN ROL r ON r.rol_id = kr.rol_id
                            WHERE i.onay_durumu = 'BEKLEMEDE'
                            """).query(String.class).list()).containsExactly("MUSTERI");
            // A business owner has also ordered from another business.
            assertThat(db.sql("""
                            SELECT COUNT(*) FROM SIPARIS s
                            JOIN ISLETME i ON i.isletme_id = s.isletme_id
                            WHERE s.musteri_id IN (SELECT yetkili_id FROM ISLETME WHERE onay_durumu = 'ONAYLANDI')
                              AND i.yetkili_id <> s.musteri_id
                            """).query(Long.class).single()).isPositive();
        });
    }

    @Test
    void liveDataGivesEveryApprovedBusinessAnActiveListingAndKeepsOneOrderOpen() {
        withDemoData(db -> {
            assertThat(db.sql("""
                            SELECT COUNT(*) FROM ISLETME i
                            WHERE i.onay_durumu = 'ONAYLANDI'
                              AND NOT EXISTS (SELECT 1 FROM PAKET_ILANI p
                                              WHERE p.isletme_id = i.isletme_id AND p.durum = 'AKTIF'
                                                AND p.teslim_baslangic > NOW() + INTERVAL 2 HOUR
                                                AND p.teslim_bitis <= NOW() + INTERVAL 4 HOUR)
                            """).query(Long.class).single()).isZero();
            assertThat(db.sql("""
                            SELECT COUNT(*) FROM SIPARIS s
                            JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
                            JOIN ODEME_ISLEMI o ON o.siparis_id = s.siparis_id
                                AND o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI'
                            WHERE s.durum = 'ONAYLANDI' AND t.teslim_zamani IS NULL
                            """).query(Long.class).single()).isEqualTo(1);
        });
    }
}
