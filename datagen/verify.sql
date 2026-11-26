-- Consistency checks run after loading generated data into end_of_day.
--
-- Every query in this file looks for rows that break a rule of the data model.
-- Every query must return zero rows; any returned row points to a defect in the
-- generator or in the loaded data.
--
-- Each query starts with a "-- check:", "-- scale:" or "-- summary:" header
-- (load.py reads the name from it) and ends with a semicolon. The scale check
-- returns a row while SIPARIS has fewer than 250,000 rows (as in a small trial
-- run), and the summaries only list row counts and status shares.

-- check: listings whose remaining stock is not the total minus the units of orders that were not cancelled
SELECT p.ilan_id, p.toplam_adet, p.kalan_adet, COALESCE(sold.adet, 0) AS satilan
FROM PAKET_ILANI p
LEFT JOIN (SELECT k.ilan_id, SUM(k.adet) AS adet
           FROM SIPARIS_KALEMI k
           JOIN SIPARIS s ON s.siparis_id = k.siparis_id
           WHERE s.durum <> 'IPTAL_EDILDI'
           GROUP BY k.ilan_id) sold ON sold.ilan_id = p.ilan_id
WHERE p.kalan_adet <> p.toplam_adet - COALESCE(sold.adet, 0);

-- check: orders with more than one successful charge
SELECT o.siparis_id, COUNT(*) AS basarili_tahsilat
FROM ODEME_ISLEMI o
WHERE o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI'
GROUP BY o.siparis_id
HAVING COUNT(*) > 1;

-- check: orders whose successful charge is missing or differs from the item total
SELECT s.siparis_id, items.toplam, charge.tutar
FROM SIPARIS s
JOIN (SELECT k.siparis_id, SUM(k.adet * k.birim_fiyat) AS toplam
      FROM SIPARIS_KALEMI k
      GROUP BY k.siparis_id) items ON items.siparis_id = s.siparis_id
LEFT JOIN (SELECT o.siparis_id, SUM(o.tutar) AS tutar
           FROM ODEME_ISLEMI o
           WHERE o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI'
           GROUP BY o.siparis_id) charge ON charge.siparis_id = s.siparis_id
WHERE charge.tutar IS NULL OR charge.tutar <> items.toplam;

-- check: orders without items
SELECT s.siparis_id
FROM SIPARIS s
WHERE NOT EXISTS (SELECT 1 FROM SIPARIS_KALEMI k WHERE k.siparis_id = s.siparis_id);

-- check: orders whose successful refunds exceed the charge
SELECT c.siparis_id, c.tutar AS tahsilat, SUM(r.tutar) AS iade
FROM ODEME_ISLEMI c
JOIN ODEME_ISLEMI r ON r.kaynak_islem_id = c.islem_id AND r.durum = 'BASARILI'
WHERE c.islem_turu = 'TAHSILAT' AND c.durum = 'BASARILI'
GROUP BY c.islem_id, c.siparis_id, c.tutar
HAVING SUM(r.tutar) > c.tutar;

-- check: cancelled orders without a full successful refund of their charge
SELECT s.siparis_id
FROM SIPARIS s
JOIN ODEME_ISLEMI c ON c.siparis_id = s.siparis_id AND c.islem_turu = 'TAHSILAT' AND c.durum = 'BASARILI'
LEFT JOIN ODEME_ISLEMI r ON r.kaynak_islem_id = c.islem_id AND r.durum = 'BASARILI'
WHERE s.durum = 'IPTAL_EDILDI'
GROUP BY s.siparis_id, c.tutar
HAVING COALESCE(SUM(r.tutar), 0) <> c.tutar;

-- check: successful refunds of orders that were not cancelled
SELECT r.islem_id, s.siparis_id, s.durum
FROM ODEME_ISLEMI r
JOIN SIPARIS s ON s.siparis_id = r.siparis_id
WHERE r.islem_turu = 'IADE' AND r.durum = 'BASARILI' AND s.durum <> 'IPTAL_EDILDI';

-- check: orders with items of a listing of another business
SELECT s.siparis_id, s.isletme_id, p.isletme_id AS ilan_isletme_id
FROM SIPARIS s
JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
JOIN PAKET_ILANI p ON p.ilan_id = k.ilan_id
WHERE p.isletme_id <> s.isletme_id;

-- check: items whose unit price is not the sale price of the listing
SELECT k.siparis_id, k.ilan_id, k.birim_fiyat, p.satis_fiyati
FROM SIPARIS_KALEMI k
JOIN PAKET_ILANI p ON p.ilan_id = k.ilan_id
WHERE k.birim_fiyat <> p.satis_fiyati;

-- check: pickup windows that are not the intersection of the windows of the order's listings
SELECT t.siparis_id
FROM TESLIM_ALMA t
JOIN SIPARIS_KALEMI k ON k.siparis_id = t.siparis_id
JOIN PAKET_ILANI p ON p.ilan_id = k.ilan_id
GROUP BY t.siparis_id, t.teslim_baslangic, t.teslim_bitis
HAVING t.teslim_baslangic <> MAX(p.teslim_baslangic) OR t.teslim_bitis <> MIN(p.teslim_bitis);

-- check: orders created at or after the start of their pickup window
SELECT s.siparis_id, s.olusturulma_zamani, t.teslim_baslangic
FROM SIPARIS s
JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
WHERE s.olusturulma_zamani >= t.teslim_baslangic;

-- check: orders without a pickup record
SELECT s.siparis_id
FROM SIPARIS s
LEFT JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
WHERE t.siparis_id IS NULL;

-- check: delivered orders picked up outside the window, and pickup times of orders that were not delivered
SELECT s.siparis_id, s.durum, t.teslim_zamani, t.teslim_baslangic, t.teslim_bitis
FROM SIPARIS s
JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
WHERE (s.durum = 'TESLIM_EDILDI'
       AND (t.teslim_zamani IS NULL OR t.teslim_zamani NOT BETWEEN t.teslim_baslangic AND t.teslim_bitis))
   OR (s.durum <> 'TESLIM_EDILDI' AND t.teslim_zamani IS NOT NULL);

-- check: pickups verified by someone other than the owner of the order's business
SELECT t.siparis_id, t.dogrulayan_id, i.yetkili_id
FROM TESLIM_ALMA t
JOIN SIPARIS s ON s.siparis_id = t.siparis_id
JOIN ISLETME i ON i.isletme_id = s.isletme_id
WHERE t.dogrulayan_id IS NOT NULL AND t.dogrulayan_id <> i.yetkili_id;

-- check: cancellations at or after the start of the pickup window
SELECT s.siparis_id, s.iptal_zamani, t.teslim_baslangic
FROM SIPARIS s
JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
WHERE s.durum = 'IPTAL_EDILDI' AND s.iptal_zamani >= t.teslim_baslangic;

-- check: reviews of orders that were not delivered, or written before the pickup
SELECT d.siparis_id, s.durum
FROM DEGERLENDIRME d
JOIN SIPARIS s ON s.siparis_id = d.siparis_id
JOIN TESLIM_ALMA t ON t.siparis_id = d.siparis_id
WHERE s.durum <> 'TESLIM_EDILDI' OR d.olusturulma_zamani <= t.teslim_zamani;

-- check: listings of businesses that are not approved and active
SELECT p.ilan_id, i.isletme_id, i.onay_durumu, i.aktif_mi
FROM PAKET_ILANI p
JOIN ISLETME i ON i.isletme_id = p.isletme_id
WHERE i.onay_durumu <> 'ONAYLANDI' OR i.aktif_mi = FALSE;

-- check: listings whose pickup window does not lie within one day
SELECT p.ilan_id, p.teslim_baslangic, p.teslim_bitis
FROM PAKET_ILANI p
WHERE DATE(p.teslim_baslangic) <> DATE(p.teslim_bitis) OR p.teslim_baslangic >= p.teslim_bitis;

-- check: orders placed before the listing was created or before the customer registered
SELECT DISTINCT s.siparis_id
FROM SIPARIS s
JOIN KULLANICI u ON u.kullanici_id = s.musteri_id
JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
JOIN PAKET_ILANI p ON p.ilan_id = k.ilan_id
WHERE s.olusturulma_zamani < p.olusturulma_zamani OR s.olusturulma_zamani < u.kayit_zamani;

-- check: confirmed orders whose pickup window has ended
SELECT s.siparis_id, t.teslim_bitis
FROM SIPARIS s
JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
WHERE s.durum = 'ONAYLANDI' AND t.teslim_bitis < NOW();

-- check: business owners without the ISLETME_YETKILISI role for an approved business
SELECT DISTINCT i.yetkili_id
FROM ISLETME i
WHERE i.onay_durumu = 'ONAYLANDI'
  AND NOT EXISTS (SELECT 1
                  FROM KULLANICI_ROL ur
                  JOIN ROL r ON r.rol_id = ur.rol_id
                  WHERE ur.kullanici_id = i.yetkili_id AND r.rol_adi = 'ISLETME_YETKILISI');

-- scale: SIPARIS has at least 250,000 rows
SELECT COUNT(*) AS siparis_sayisi
FROM SIPARIS
HAVING COUNT(*) < 250000;

-- summary: rows per table
SELECT 'KULLANICI' AS tablo, COUNT(*) AS satir FROM KULLANICI
UNION ALL SELECT 'KULLANICI_ROL', COUNT(*) FROM KULLANICI_ROL
UNION ALL SELECT 'ISLETME', COUNT(*) FROM ISLETME
UNION ALL SELECT 'PAKET_ILANI', COUNT(*) FROM PAKET_ILANI
UNION ALL SELECT 'SIPARIS', COUNT(*) FROM SIPARIS
UNION ALL SELECT 'SIPARIS_KALEMI', COUNT(*) FROM SIPARIS_KALEMI
UNION ALL SELECT 'ODEME_ISLEMI', COUNT(*) FROM ODEME_ISLEMI
UNION ALL SELECT 'TESLIM_ALMA', COUNT(*) FROM TESLIM_ALMA
UNION ALL SELECT 'DEGERLENDIRME', COUNT(*) FROM DEGERLENDIRME;

-- summary: order status shares
SELECT durum, COUNT(*) AS siparis, ROUND(100 * COUNT(*) / (SELECT COUNT(*) FROM SIPARIS), 2) AS yuzde
FROM SIPARIS
GROUP BY durum
ORDER BY siparis DESC;
