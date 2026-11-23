# Analytical queries

The five analytical queries of the project report (section 8.2) are in
[database/analytics/analytical-queries.sql](../database/analytics/analytical-queries.sql). Each one starts with a
`-- Qn:` header. `AnalyticalQueriesIntegrationTest` runs them on the demo data and compares the results with the tables
below, which were computed independently of the SQL from the raw demo rows.

## Shared definitions

| Term | Definition |
| --- | --- |
| Sales | The items (SIPARIS_KALEMI) of orders whose status is not IPTAL_EDILDI. |
| Net collection | Successful TAHSILAT minus successful IADE amounts. |
| Not-picked-up rate | TESLIM_ALINMADI / (TESLIM_EDILDI + TESLIM_ALINMADI). ONAYLANDI and IPTAL_EDILDI orders are not counted. |
| Month | YYYY-MM of the order's pickup start (TESLIM_ALMA.teslim_baslangic). |

Ratios and averages are rounded to two decimal places; threshold comparisons use the unrounded value.

Q1 and Q5 are monthly. The live part of the demo data falls into the month in which it is loaded, so only September and
October 2026 are fixed. Q2, Q3 and Q4 cover all time and include the one confirmed live order.

## Q1: Businesses above the monthly average net collection

- **Purpose:** find the businesses that collect more than the average business in the same month.
- **Tables:** ISLETME, SIPARIS, ODEME_ISLEMI, and TESLIM_ALMA for the month.
- **Structure:** GROUP BY month and business; HAVING compares the net collection with a correlated subquery that
  averages the net collection of all businesses with payments in the same month. The subqueries refer to the month
  through the select alias `ay`, because MySQL evaluates a column of a merged derived table inside HAVING against the
  wrong group.
- **Output:** ay, isletme_id, isletme_adi, net_tahsilat, ay_ortalamasi.

| ay | isletme_adi | net_tahsilat | ay_ortalamasi |
| --- | --- | --- | --- |
| 2026-09 | Moda Kahve Evi | 545.00 | 267.50 |
| 2026-09 | Ertaş Kafe | 325.00 | 267.50 |
| 2026-09 | Beşiktaş Lokantası | 300.00 | 267.50 |
| 2026-10 | Moda Kahve Evi | 430.00 | 196.67 |
| 2026-10 | Karaca Fırını Kızılay Şubesi | 400.00 | 196.67 |

In September, Karaca Fırını Kızılay Şubesi (185.00), Şahin Ev Yemekleri (180.00) and Karaca Fırını Bahçelievler Şubesi
(70.00) stay below the average. Cancelled orders add their charge and their full refund, so they contribute 0.

## Q2: Businesses selling more than their district average

- **Purpose:** compare each business with the other approved businesses of its city and district.
- **Tables:** ISLETME, SIPARIS, SIPARIS_KALEMI.
- **Structure:** GROUP BY business; HAVING compares the units sold with a correlated subquery that averages the units
  sold by the approved businesses of the same (sehir, ilce). A business without sales counts as 0 (LEFT JOIN and
  COALESCE in the subquery).
- **Output:** sehir, ilce, isletme_adi, satilan_adet, ilce_ortalamasi.

| sehir | ilce | isletme_adi | satilan_adet | ilce_ortalamasi |
| --- | --- | --- | --- | --- |
| Ankara | Çankaya | Karaca Fırını Kızılay Şubesi | 11 | 7.00 |
| İstanbul | Kadıköy | Moda Kahve Evi | 17 | 10.50 |

Keçiören and Beşiktaş have one approved business each, which is never above its own average. The pending business
in Yenimahalle is not approved and is not counted.

## Q3: The three best-selling listings of every city

- **Purpose:** show which packages sell best in each city.
- **Tables:** ISLETME, PAKET_ILANI, SIPARIS_KALEMI, SIPARIS.
- **Structure:** units sold per listing in a derived table with `DENSE_RANK() OVER (PARTITION BY sehir ORDER BY
  SUM(adet) DESC)`; the outer query keeps ranks 1 to 3. Listings with equal sales share a rank, so a city can return
  more than three rows.
- **Output:** sehir, sira, ilan_id, baslik, isletme_adi, satilan_adet.

| sehir | sira | baslik | isletme_adi | satilan_adet |
| --- | --- | --- | --- | --- |
| Ankara | 1 | Sandviç ve Kek Paketi | Ertaş Kafe | 5 |
| Ankara | 2 | Hafta Sonu Börek Paketi | Karaca Fırını Kızılay Şubesi | 4 |
| Ankara | 3 | Akşam Ekmek Paketi | Karaca Fırını Kızılay Şubesi | 3 |
| İstanbul | 1 | Kruvasan Paketi | Moda Kahve Evi | 7 |
| İstanbul | 2 | Kahvaltılık Paketi | Moda Kahve Evi | 4 |
| İstanbul | 3 | Akşam Yemeği Paketi | Beşiktaş Lokantası | 3 |
| İstanbul | 3 | Brownie Paketi | Moda Kahve Evi | 3 |

İstanbul shows the tie case: two listings share rank 3. `ilan_id` is generated and therefore not listed here.

## Q4: Where repeat customers shop

- **Purpose:** show which businesses attract the customers who come back to the platform.
- **Tables:** KULLANICI, SIPARIS, ISLETME.
- **Structure:** a subquery with GROUP BY and HAVING selects repeat customers (at least two orders on the platform that
  were not cancelled). The outer query groups their orders that were not cancelled by business; HAVING keeps businesses
  with at least three repeat customers. No personal field is returned.
- **Output:** isletme_adi, tekrar_eden_musteri_sayisi, siparis_sayisi.

| isletme_adi | tekrar_eden_musteri_sayisi | siparis_sayisi |
| --- | --- | --- |
| Moda Kahve Evi | 5 | 8 |
| Karaca Fırını Kızılay Şubesi | 5 | 6 |
| Beşiktaş Lokantası | 3 | 3 |

The demo data has 7 repeat customers and 6 customers with a single order that was not cancelled.

## Q5: Monthly not-picked-up rate above the platform rate

- **Purpose:** find the businesses and months in which customers often fail to pick up their orders.
- **Tables:** ISLETME, SIPARIS, TESLIM_ALMA.
- **Structure:** conditional aggregation (`SUM(CASE ...)`) per business and month; HAVING keeps the rates above the
  rate of the whole platform over all months, computed by a subquery. Sorted by rate, highest first.
- **Output:** ay, isletme_adi, teslim_alinmayan, toplam_teslim_durumu, oran, platform_orani.

| ay | isletme_adi | teslim_alinmayan | toplam_teslim_durumu | oran | platform_orani |
| --- | --- | --- | --- | --- | --- |
| 2026-09 | Karaca Fırını Bahçelievler Şubesi | 1 | 2 | 0.50 | 0.18 |
| 2026-09 | Şahin Ev Yemekleri | 1 | 2 | 0.50 | 0.18 |
| 2026-10 | Şahin Ev Yemekleri | 1 | 2 | 0.50 | 0.18 |
| 2026-09 | Karaca Fırını Kızılay Şubesi | 1 | 3 | 0.33 | 0.18 |
| 2026-10 | Moda Kahve Evi | 1 | 4 | 0.25 | 0.18 |

The platform rate is 5 / 28 = 0.18: 5 orders not picked up out of 28 that were delivered or not picked up.
