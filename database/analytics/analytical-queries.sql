-- Analytical queries of the project report (section 8.2).
--
-- Shared definitions:
-- * sales: the items (SIPARIS_KALEMI) of orders whose status is not
--   IPTAL_EDILDI.
-- * net collection: successful TAHSILAT minus successful IADE amounts.
-- * not-picked-up rate: TESLIM_ALINMADI / (TESLIM_EDILDI + TESLIM_ALINMADI).
--   Orders that are still ONAYLANDI or were cancelled are not counted.
-- * month: YYYY-MM of the order's pickup start (TESLIM_ALMA.teslim_baslangic).
--
-- Ratios and averages are rounded to two decimal places. Comparisons with a
-- threshold use the unrounded value.
--
-- Each query starts with a "-- Qn:" header and ends with a semicolon.


-- Q1: Businesses whose monthly net collection exceeds the monthly average
-- Net collection per business and month (ISLETME + SIPARIS + ODEME_ISLEMI,
-- with TESLIM_ALMA for the month). HAVING keeps the businesses above the
-- average net collection of all businesses with payments in the same month,
-- which a correlated subquery computes. The subqueries refer to the month
-- through the select alias ay: MySQL evaluates a column of a merged derived
-- table inside HAVING against the wrong group, so the month is not taken
-- from a derived table.
SELECT
    DATE_FORMAT(t.teslim_baslangic, '%Y-%m') AS ay,
    i.isletme_id,
    i.isletme_adi,
    SUM(CASE
            WHEN o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI' THEN o.tutar
            WHEN o.islem_turu = 'IADE' AND o.durum = 'BASARILI' THEN -o.tutar
            ELSE 0
        END) AS net_tahsilat,
    (SELECT ROUND(AVG(m.net_tahsilat), 2)
     FROM (SELECT s2.isletme_id,
                  DATE_FORMAT(t2.teslim_baslangic, '%Y-%m') AS aylik,
                  SUM(CASE
                          WHEN o2.islem_turu = 'TAHSILAT' AND o2.durum = 'BASARILI' THEN o2.tutar
                          WHEN o2.islem_turu = 'IADE' AND o2.durum = 'BASARILI' THEN -o2.tutar
                          ELSE 0
                      END) AS net_tahsilat
           FROM SIPARIS s2
           JOIN TESLIM_ALMA t2 ON t2.siparis_id = s2.siparis_id
           JOIN ODEME_ISLEMI o2 ON o2.siparis_id = s2.siparis_id
           GROUP BY s2.isletme_id, DATE_FORMAT(t2.teslim_baslangic, '%Y-%m')) m
     WHERE m.aylik = ay) AS ay_ortalamasi
FROM ISLETME i
JOIN SIPARIS s ON s.isletme_id = i.isletme_id
JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
JOIN ODEME_ISLEMI o ON o.siparis_id = s.siparis_id
GROUP BY ay, i.isletme_id, i.isletme_adi
HAVING net_tahsilat > (SELECT AVG(m.net_tahsilat)
                       FROM (SELECT s2.isletme_id,
                                    DATE_FORMAT(t2.teslim_baslangic, '%Y-%m') AS aylik,
                                    SUM(CASE
                                            WHEN o2.islem_turu = 'TAHSILAT' AND o2.durum = 'BASARILI' THEN o2.tutar
                                            WHEN o2.islem_turu = 'IADE' AND o2.durum = 'BASARILI' THEN -o2.tutar
                                            ELSE 0
                                        END) AS net_tahsilat
                             FROM SIPARIS s2
                             JOIN TESLIM_ALMA t2 ON t2.siparis_id = s2.siparis_id
                             JOIN ODEME_ISLEMI o2 ON o2.siparis_id = s2.siparis_id
                             GROUP BY s2.isletme_id, DATE_FORMAT(t2.teslim_baslangic, '%Y-%m')) m
                       WHERE m.aylik = ay)
ORDER BY ay, net_tahsilat DESC, i.isletme_adi;


-- Q2: Businesses that sell more than the average of their district
-- Units sold per business (ISLETME + SIPARIS + SIPARIS_KALEMI), over all
-- time. HAVING compares them with the average of the approved businesses in
-- the same city and district, computed by a correlated subquery in which a
-- business without sales counts as 0.
SELECT
    i.sehir,
    i.ilce,
    i.isletme_adi,
    SUM(k.adet) AS satilan_adet,
    (SELECT ROUND(AVG(d.satilan_adet), 2)
     FROM (SELECT i2.isletme_id, i2.sehir, i2.ilce, COALESCE(SUM(k2.adet), 0) AS satilan_adet
           FROM ISLETME i2
           LEFT JOIN SIPARIS s2 ON s2.isletme_id = i2.isletme_id AND s2.durum <> 'IPTAL_EDILDI'
           LEFT JOIN SIPARIS_KALEMI k2 ON k2.siparis_id = s2.siparis_id
           WHERE i2.onay_durumu = 'ONAYLANDI'
           GROUP BY i2.isletme_id, i2.sehir, i2.ilce) d
     WHERE d.sehir = i.sehir AND d.ilce = i.ilce) AS ilce_ortalamasi
FROM ISLETME i
JOIN SIPARIS s ON s.isletme_id = i.isletme_id AND s.durum <> 'IPTAL_EDILDI'
JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
WHERE i.onay_durumu = 'ONAYLANDI'
GROUP BY i.isletme_id, i.sehir, i.ilce, i.isletme_adi
HAVING SUM(k.adet) > (SELECT AVG(d.satilan_adet)
                      FROM (SELECT i2.isletme_id, i2.sehir, i2.ilce, COALESCE(SUM(k2.adet), 0) AS satilan_adet
                            FROM ISLETME i2
                            LEFT JOIN SIPARIS s2 ON s2.isletme_id = i2.isletme_id AND s2.durum <> 'IPTAL_EDILDI'
                            LEFT JOIN SIPARIS_KALEMI k2 ON k2.siparis_id = s2.siparis_id
                            WHERE i2.onay_durumu = 'ONAYLANDI'
                            GROUP BY i2.isletme_id, i2.sehir, i2.ilce) d
                      WHERE d.sehir = i.sehir AND d.ilce = i.ilce)
ORDER BY i.sehir, i.ilce, satilan_adet DESC;


-- Q3: The three best-selling listings of every city
-- Units sold per listing (ISLETME + PAKET_ILANI + SIPARIS_KALEMI + SIPARIS),
-- ranked within each city with DENSE_RANK in a derived table. The outer query
-- keeps ranks 1 to 3. Listings with equal sales share a rank, so a city can
-- return more than three rows.
SELECT r.sehir, r.sira, r.ilan_id, r.baslik, r.isletme_adi, r.satilan_adet
FROM (SELECT i.sehir,
             p.ilan_id,
             p.baslik,
             i.isletme_adi,
             SUM(k.adet) AS satilan_adet,
             DENSE_RANK() OVER (PARTITION BY i.sehir ORDER BY SUM(k.adet) DESC) AS sira
      FROM ISLETME i
      JOIN PAKET_ILANI p ON p.isletme_id = i.isletme_id
      JOIN SIPARIS_KALEMI k ON k.ilan_id = p.ilan_id
      JOIN SIPARIS s ON s.siparis_id = k.siparis_id
      WHERE s.durum <> 'IPTAL_EDILDI'
      GROUP BY i.sehir, p.ilan_id, p.baslik, i.isletme_adi) r
WHERE r.sira <= 3
ORDER BY r.sehir, r.sira, r.baslik;


-- Q4: Where repeat customers shop
-- A repeat customer has at least two orders on the platform that were not
-- cancelled (subquery). Per business (KULLANICI + SIPARIS + ISLETME), the
-- number of such customers and of their orders that were not cancelled.
-- HAVING keeps businesses with at least three repeat customers. No personal
-- field is returned.
SELECT
    i.isletme_adi,
    COUNT(DISTINCT u.kullanici_id) AS tekrar_eden_musteri_sayisi,
    COUNT(*) AS siparis_sayisi
FROM KULLANICI u
JOIN SIPARIS s ON s.musteri_id = u.kullanici_id AND s.durum <> 'IPTAL_EDILDI'
JOIN ISLETME i ON i.isletme_id = s.isletme_id
WHERE u.kullanici_id IN (SELECT s2.musteri_id
                         FROM SIPARIS s2
                         WHERE s2.durum <> 'IPTAL_EDILDI'
                         GROUP BY s2.musteri_id
                         HAVING COUNT(*) >= 2)
GROUP BY i.isletme_id, i.isletme_adi
HAVING COUNT(DISTINCT u.kullanici_id) >= 3
ORDER BY tekrar_eden_musteri_sayisi DESC, siparis_sayisi DESC, i.isletme_adi;


-- Q5: Businesses whose monthly not-picked-up rate exceeds the platform rate
-- Conditional aggregation per business and month (ISLETME + SIPARIS +
-- TESLIM_ALMA). HAVING keeps the rates above the rate of the whole platform
-- over all months, computed by a subquery. Highest rates first.
SELECT
    DATE_FORMAT(t.teslim_baslangic, '%Y-%m') AS ay,
    i.isletme_adi,
    SUM(CASE WHEN s.durum = 'TESLIM_ALINMADI' THEN 1 ELSE 0 END) AS teslim_alinmayan,
    SUM(CASE WHEN s.durum IN ('TESLIM_EDILDI', 'TESLIM_ALINMADI') THEN 1 ELSE 0 END) AS toplam_teslim_durumu,
    ROUND(SUM(CASE WHEN s.durum = 'TESLIM_ALINMADI' THEN 1 ELSE 0 END)
          / NULLIF(SUM(CASE WHEN s.durum IN ('TESLIM_EDILDI', 'TESLIM_ALINMADI') THEN 1 ELSE 0 END), 0), 2) AS oran,
    (SELECT ROUND(SUM(CASE WHEN s2.durum = 'TESLIM_ALINMADI' THEN 1 ELSE 0 END)
                  / NULLIF(SUM(CASE WHEN s2.durum IN ('TESLIM_EDILDI', 'TESLIM_ALINMADI') THEN 1 ELSE 0 END), 0), 2)
     FROM SIPARIS s2) AS platform_orani
FROM ISLETME i
JOIN SIPARIS s ON s.isletme_id = i.isletme_id
JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
GROUP BY DATE_FORMAT(t.teslim_baslangic, '%Y-%m'), i.isletme_id, i.isletme_adi
HAVING SUM(CASE WHEN s.durum = 'TESLIM_ALINMADI' THEN 1 ELSE 0 END)
       / NULLIF(SUM(CASE WHEN s.durum IN ('TESLIM_EDILDI', 'TESLIM_ALINMADI') THEN 1 ELSE 0 END), 0)
       > (SELECT SUM(CASE WHEN s2.durum = 'TESLIM_ALINMADI' THEN 1 ELSE 0 END)
                 / NULLIF(SUM(CASE WHEN s2.durum IN ('TESLIM_EDILDI', 'TESLIM_ALINMADI') THEN 1 ELSE 0 END), 0)
          FROM SIPARIS s2)
ORDER BY oran DESC, ay, i.isletme_adi;
