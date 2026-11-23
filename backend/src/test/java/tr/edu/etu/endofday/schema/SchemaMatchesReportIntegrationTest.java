package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.support.IntegrationTest;

/**
 * Guards the schema against any drift from the project report.
 *
 * <p>The report defines every table and the exact names and order of its columns. This test reads the schema that the
 * migrations actually produce and compares it with that definition, so that adding, removing, renaming or reordering a
 * table or column fails the build instead of silently contradicting the report.
 */
@Transactional
class SchemaMatchesReportIntegrationTest extends IntegrationTest {

    private static final String SCHEMA = "end_of_day";

    private static final Map<String, List<String>> REPORT_TABLES = reportTables();

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void databaseContainsExactlyTheReportTables() {
        List<String> tables = jdbcClient
                .sql("SELECT TABLE_NAME FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = :schema AND TABLE_TYPE = 'BASE TABLE'"
                        + " AND TABLE_NAME <> 'flyway_schema_history'")
                .param("schema", SCHEMA)
                .query(String.class)
                .list();

        assertThat(tables).containsExactlyInAnyOrderElementsOf(REPORT_TABLES.keySet());
    }

    @Test
    void everyTableHasExactlyTheReportColumnsInTheReportOrder() {
        Map<String, List<String>> actual = new LinkedHashMap<>();
        for (String table : REPORT_TABLES.keySet()) {
            actual.put(
                    table,
                    jdbcClient
                            .sql("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                                    + " WHERE TABLE_SCHEMA = :schema AND TABLE_NAME = :table"
                                    + " ORDER BY ORDINAL_POSITION")
                            .param("schema", SCHEMA)
                            .param("table", table)
                            .query(String.class)
                            .list());
        }

        assertThat(actual).containsExactlyEntriesOf(REPORT_TABLES);
    }

    @Test
    void roleTableHoldsExactlyTheThreeInitialRoles() {
        List<String> roles =
                jdbcClient.sql("SELECT rol_adi FROM ROL").query(String.class).list();

        assertThat(roles).hasSize(3).containsExactlyInAnyOrder("MUSTERI", "ISLETME_YETKILISI", "YONETICI");
    }

    private static Map<String, List<String>> reportTables() {
        Map<String, List<String>> tables = new LinkedHashMap<>();
        tables.put(
                "KULLANICI",
                List.of(
                        "kullanici_id",
                        "ad",
                        "soyad",
                        "eposta",
                        "telefon",
                        "parola_ozeti",
                        "aktif_mi",
                        "kayit_zamani"));
        tables.put("ROL", List.of("rol_id", "rol_adi"));
        tables.put("KULLANICI_ROL", List.of("kullanici_id", "rol_id"));
        tables.put(
                "ISLETME",
                List.of(
                        "isletme_id",
                        "yetkili_id",
                        "isletme_adi",
                        "isletme_turu",
                        "sehir",
                        "ilce",
                        "adres",
                        "telefon",
                        "onay_durumu",
                        "aktif_mi"));
        tables.put(
                "PAKET_ILANI",
                List.of(
                        "ilan_id",
                        "isletme_id",
                        "baslik",
                        "aciklama",
                        "alerjen_bilgisi",
                        "normal_fiyat",
                        "satis_fiyati",
                        "toplam_adet",
                        "kalan_adet",
                        "teslim_baslangic",
                        "teslim_bitis",
                        "durum",
                        "olusturulma_zamani"));
        tables.put(
                "SIPARIS",
                List.of("siparis_id", "musteri_id", "isletme_id", "olusturulma_zamani", "durum", "iptal_zamani"));
        tables.put("SIPARIS_KALEMI", List.of("siparis_id", "ilan_id", "adet", "birim_fiyat"));
        tables.put(
                "ODEME_ISLEMI",
                List.of(
                        "islem_id",
                        "siparis_id",
                        "kaynak_islem_id",
                        "istek_no",
                        "islem_turu",
                        "tutar",
                        "durum",
                        "islem_zamani"));
        tables.put(
                "TESLIM_ALMA",
                List.of(
                        "siparis_id",
                        "teslim_kodu",
                        "teslim_baslangic",
                        "teslim_bitis",
                        "teslim_zamani",
                        "dogrulayan_id"));
        tables.put("DEGERLENDIRME", List.of("siparis_id", "puan", "yorum", "olusturulma_zamani"));
        return tables;
    }
}
