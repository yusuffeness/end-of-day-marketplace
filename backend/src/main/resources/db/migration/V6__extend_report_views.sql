-- Extends the reporting views so that the platform report of the administrator
-- (GET /api/v1/admin/reports/platform) can be read by eod_report from views
-- only, for any date range:
--
-- * v_order_summary gets the column adet (sum of item quantities), so that
--   units sold can be counted per day and not only per month.
-- * v_review_summary: ratings per order, without the free-text comment.
-- * v_business_status: type, location, approval and active flag of every
--   business, without name, address or phone.
-- * v_user_registrations: registration date and active flag of every account,
--   without any identifying column.
--
-- As in V4, no view selects ad, soyad, eposta, telefon, parola_ozeti, adres or
-- yorum, every view runs with the privileges of its definer, and eod_report
-- gets SELECT on the views only.

-- adet is appended as the last column, so the existing columns keep their
-- positions.
CREATE OR REPLACE SQL SECURITY DEFINER VIEW v_order_summary AS
SELECT
    s.siparis_id,
    s.isletme_id,
    i.isletme_turu,
    i.sehir,
    i.ilce,
    s.olusturulma_zamani,
    s.durum,
    COUNT(k.ilan_id) AS kalem_sayisi,
    COALESCE(SUM(k.adet * k.birim_fiyat), 0) AS toplam_tutar,
    COALESCE(SUM(k.adet), 0) AS adet
FROM SIPARIS s
JOIN ISLETME i ON i.isletme_id = s.isletme_id
LEFT JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
GROUP BY s.siparis_id, s.isletme_id, i.isletme_turu, i.sehir, i.ilce, s.olusturulma_zamani, s.durum;

-- One row per review with the business and the creation time of its order.
CREATE SQL SECURITY DEFINER VIEW v_review_summary AS
SELECT
    d.siparis_id,
    s.isletme_id,
    s.olusturulma_zamani AS siparis_zamani,
    d.puan,
    d.olusturulma_zamani AS degerlendirme_zamani
FROM DEGERLENDIRME d
JOIN SIPARIS s ON s.siparis_id = d.siparis_id;

-- One row per business.
CREATE SQL SECURITY DEFINER VIEW v_business_status AS
SELECT
    isletme_id,
    isletme_turu,
    sehir,
    ilce,
    onay_durumu,
    aktif_mi
FROM ISLETME;

-- One row per account: only the registration date and the active flag.
CREATE SQL SECURITY DEFINER VIEW v_user_registrations AS
SELECT
    DATE(kayit_zamani) AS kayit_tarihi,
    aktif_mi
FROM KULLANICI;

GRANT SELECT ON v_order_summary TO 'eod_report'@'%';
GRANT SELECT ON v_review_summary TO 'eod_report'@'%';
GRANT SELECT ON v_business_status TO 'eod_report'@'%';
GRANT SELECT ON v_user_registrations TO 'eod_report'@'%';
