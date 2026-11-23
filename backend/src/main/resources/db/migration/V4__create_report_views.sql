-- Reporting views for the read-only account eod_report.
--
-- The views expose no personal data: none of them selects ad, soyad, eposta,
-- telefon, parola_ozeti or adres. They run with the privileges of their
-- definer, so eod_report needs SELECT on the views only, never on the base
-- tables.

-- One row per order with its business location, number of items (order
-- lines) and total amount.
CREATE SQL SECURITY DEFINER VIEW v_order_summary AS
SELECT
    s.siparis_id,
    s.isletme_id,
    i.isletme_turu,
    i.sehir,
    i.ilce,
    s.olusturulma_zamani,
    s.durum,
    COUNT(k.ilan_id) AS kalem_sayisi,
    COALESCE(SUM(k.adet * k.birim_fiyat), 0) AS toplam_tutar
FROM SIPARIS s
JOIN ISLETME i ON i.isletme_id = s.isletme_id
LEFT JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
GROUP BY s.siparis_id, s.isletme_id, i.isletme_turu, i.sehir, i.ilce, s.olusturulma_zamani, s.durum;

-- One row per business and calendar month (YYYY-MM, by order creation time).
-- siparis_sayisi counts all orders, iptal_sayisi the cancelled ones and
-- satilan_adet the item quantities of every order that was not cancelled.
CREATE SQL SECURITY DEFINER VIEW v_business_sales AS
SELECT
    i.isletme_id,
    i.isletme_adi,
    i.isletme_turu,
    i.sehir,
    i.ilce,
    o.ay,
    COUNT(*) AS siparis_sayisi,
    SUM(CASE WHEN o.durum = 'IPTAL_EDILDI' THEN 1 ELSE 0 END) AS iptal_sayisi,
    SUM(CASE WHEN o.durum = 'IPTAL_EDILDI' THEN 0 ELSE o.adet END) AS satilan_adet
FROM ISLETME i
JOIN (
    SELECT
        s.siparis_id,
        s.isletme_id,
        s.durum,
        DATE_FORMAT(s.olusturulma_zamani, '%Y-%m') AS ay,
        COALESCE(SUM(k.adet), 0) AS adet
    FROM SIPARIS s
    LEFT JOIN SIPARIS_KALEMI k ON k.siparis_id = s.siparis_id
    GROUP BY s.siparis_id, s.isletme_id, s.durum, DATE_FORMAT(s.olusturulma_zamani, '%Y-%m')
) o ON o.isletme_id = i.isletme_id
GROUP BY i.isletme_id, i.isletme_adi, i.isletme_turu, i.sehir, i.ilce, o.ay;

-- One row per order: the successful charge, the sum of successful refunds
-- and the net amount kept. Orders without payments show zero amounts.
CREATE SQL SECURITY DEFINER VIEW v_payment_summary AS
SELECT
    p.siparis_id,
    p.isletme_id,
    p.tahsilat_tutari,
    p.iade_tutari,
    p.tahsilat_tutari - p.iade_tutari AS net_tutar
FROM (
    SELECT
        s.siparis_id,
        s.isletme_id,
        COALESCE(SUM(CASE WHEN o.islem_turu = 'TAHSILAT' AND o.durum = 'BASARILI' THEN o.tutar END), 0)
            AS tahsilat_tutari,
        COALESCE(SUM(CASE WHEN o.islem_turu = 'IADE' AND o.durum = 'BASARILI' THEN o.tutar END), 0)
            AS iade_tutari
    FROM SIPARIS s
    LEFT JOIN ODEME_ISLEMI o ON o.siparis_id = s.siparis_id
    GROUP BY s.siparis_id, s.isletme_id
) p;
