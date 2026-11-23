package tr.edu.etu.endofday.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.edu.etu.endofday.schema.DemoData.withDemoData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import tr.edu.etu.endofday.support.IntegrationTest;

/**
 * Runs the five analytical queries of database/analytics/analytical-queries.sql (report section 8.2) on the demo data
 * and compares them with expected results.
 *
 * <p>The expected rows were computed independently of the SQL, from the raw demo rows (orders, items, payments and
 * pickup windows) with the definitions at the top of the query file. Generated identifiers are not compared directly:
 * they are checked to belong to the business or listing named in the same row. Q1 and Q5 are monthly, so only
 * September and October 2026 are compared; the live demo rows fall into the month in which the file is loaded.
 */
class AnalyticalQueriesIntegrationTest extends IntegrationTest {

    private static final Path QUERY_FILE = Path.of("..", "database", "analytics", "analytical-queries.sql");

    private static final Pattern QUERY_HEADER = Pattern.compile("(?m)^-- (Q[1-5]):");

    private static final List<String> HISTORICAL_MONTHS = List.of("2026-09", "2026-10");

    private static Map<String, String> queries;

    @BeforeAll
    static void readQueries() throws IOException {
        // Each query runs from its "-- Qn:" header to the next header (or the end of the file) and ends with ";".
        String text = Files.readString(QUERY_FILE, StandardCharsets.UTF_8);
        List<MatchResult> headers = QUERY_HEADER.matcher(text).results().toList();
        queries = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            int end = i + 1 < headers.size() ? headers.get(i + 1).start() : text.length();
            String block = text.substring(headers.get(i).start(), end).strip();
            assertThat(block).as(headers.get(i).group(1)).endsWith(";");
            queries.put(headers.get(i).group(1), block.substring(0, block.length() - 1));
        }
    }

    @Test
    void fileContainsTheFiveQueriesInOrder() {
        assertThat(queries).containsOnlyKeys("Q1", "Q2", "Q3", "Q4", "Q5");
        assertThat(queries.keySet()).containsExactly("Q1", "Q2", "Q3", "Q4", "Q5");
    }

    @Test
    void q1ListsBusinessesAboveTheMonthlyAverageNetCollection() {
        withDemoData(db -> {
            List<Map<String, Object>> rows = historicalMonths(rows(db, "Q1"));
            assertIdsMatchBusinessNames(db, rows);

            assertThat(format(rows, "ay", "isletme_adi", "net_tahsilat", "ay_ortalamasi"))
                    .containsExactly(
                            "2026-09 | Moda Kahve Evi | 545.00 | 267.50",
                            "2026-09 | Ertaş Kafe | 325.00 | 267.50",
                            "2026-09 | Beşiktaş Lokantası | 300.00 | 267.50",
                            "2026-10 | Moda Kahve Evi | 430.00 | 196.67",
                            "2026-10 | Karaca Fırını Kızılay Şubesi | 400.00 | 196.67");
        });
    }

    @Test
    void q2ListsBusinessesSellingMoreThanTheirDistrictAverage() {
        withDemoData(db -> assertThat(
                        format(rows(db, "Q2"), "sehir", "ilce", "isletme_adi", "satilan_adet", "ilce_ortalamasi"))
                .containsExactly(
                        "Ankara | Çankaya | Karaca Fırını Kızılay Şubesi | 11 | 7.00",
                        "İstanbul | Kadıköy | Moda Kahve Evi | 17 | 10.50"));
    }

    @Test
    void q3ListsTheThreeBestSellingListingsOfEveryCityIncludingTies() {
        withDemoData(db -> {
            List<Map<String, Object>> rows = rows(db, "Q3");
            for (Map<String, Object> row : rows) {
                String title = db.sql("SELECT baslik FROM PAKET_ILANI WHERE ilan_id = :id")
                        .param("id", row.get("ilan_id"))
                        .query(String.class)
                        .single();
                assertThat(title).isEqualTo(row.get("baslik"));
            }

            assertThat(format(rows, "sehir", "sira", "baslik", "isletme_adi", "satilan_adet"))
                    .containsExactly(
                            "Ankara | 1 | Sandviç ve Kek Paketi | Ertaş Kafe | 5",
                            "Ankara | 2 | Hafta Sonu Börek Paketi | Karaca Fırını Kızılay Şubesi | 4",
                            "Ankara | 3 | Akşam Ekmek Paketi | Karaca Fırını Kızılay Şubesi | 3",
                            "İstanbul | 1 | Kruvasan Paketi | Moda Kahve Evi | 7",
                            "İstanbul | 2 | Kahvaltılık Paketi | Moda Kahve Evi | 4",
                            "İstanbul | 3 | Akşam Yemeği Paketi | Beşiktaş Lokantası | 3",
                            "İstanbul | 3 | Brownie Paketi | Moda Kahve Evi | 3");
        });
    }

    @Test
    void q4ListsWhereRepeatCustomersShopWithoutPersonalFields() {
        withDemoData(db -> {
            List<Map<String, Object>> rows = rows(db, "Q4");

            assertThat(rows.getFirst().keySet())
                    .containsExactly("isletme_adi", "tekrar_eden_musteri_sayisi", "siparis_sayisi");
            assertThat(format(rows, "isletme_adi", "tekrar_eden_musteri_sayisi", "siparis_sayisi"))
                    .containsExactly(
                            "Moda Kahve Evi | 5 | 8",
                            "Karaca Fırını Kızılay Şubesi | 5 | 6",
                            "Beşiktaş Lokantası | 3 | 3");
        });
    }

    @Test
    void q5ListsMonthlyNotPickedUpRatesAboveThePlatformRate() {
        withDemoData(db -> assertThat(format(
                        historicalMonths(rows(db, "Q5")),
                        "ay",
                        "isletme_adi",
                        "teslim_alinmayan",
                        "toplam_teslim_durumu",
                        "oran",
                        "platform_orani"))
                .containsExactly(
                        "2026-09 | Karaca Fırını Bahçelievler Şubesi | 1 | 2 | 0.50 | 0.18",
                        "2026-09 | Şahin Ev Yemekleri | 1 | 2 | 0.50 | 0.18",
                        "2026-10 | Şahin Ev Yemekleri | 1 | 2 | 0.50 | 0.18",
                        "2026-09 | Karaca Fırını Kızılay Şubesi | 1 | 3 | 0.33 | 0.18",
                        "2026-10 | Moda Kahve Evi | 1 | 4 | 0.25 | 0.18"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Q1", "Q2", "Q3", "Q4", "Q5"})
    void explainAcceptsTheQuery(String name) {
        withDemoData(db -> {
            // EXPLAIN takes the statement itself; the text comes from the query file, not from input.
            List<Map<String, Object>> plan =
                    db.sql("EXPLAIN " + queries.get(name)).query().listOfRows();

            assertThat(plan).isNotEmpty();
        });
    }

    private static List<Map<String, Object>> rows(JdbcClient db, String name) {
        return db.sql(queries.get(name)).query().listOfRows();
    }

    private static List<Map<String, Object>> historicalMonths(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(row -> HISTORICAL_MONTHS.contains(row.get("ay")))
                .toList();
    }

    private static void assertIdsMatchBusinessNames(JdbcClient db, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String name = db.sql("SELECT isletme_adi FROM ISLETME WHERE isletme_id = :id")
                    .param("id", row.get("isletme_id"))
                    .query(String.class)
                    .single();
            assertThat(name).isEqualTo(row.get("isletme_adi"));
        }
    }

    private static List<String> format(List<Map<String, Object>> rows, String... columns) {
        return rows.stream()
                .map(row -> String.join(
                        " | ",
                        Arrays.stream(columns)
                                .map(column -> String.valueOf(row.get(column)))
                                .toList()))
                .toList();
    }
}
