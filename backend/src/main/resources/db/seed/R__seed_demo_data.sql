-- Demo data for the dev profile (Flyway repeatable migration).
--
-- Loaded only by the dev profile (spring.flyway.locations includes
-- classpath:db/seed) and applied again whenever this file changes.
--
-- WARNING: the first section deletes ALL rows of every table except ROL,
-- including data that was not created by this file.
--
-- Conventions:
-- * No identifier is written as a literal. Foreign keys are looked up through
--   unique values (eposta, isletme_adi, baslik + isletme) or taken from
--   LAST_INSERT_ID() right after the parent row is inserted.
-- * Every row passes the constraints and triggers of the schema, and nothing is
--   disabled while loading.
-- * Names, businesses and addresses are fictional. All demo accounts share
--   the password GunSonu2026! (BCrypt, cost 12).
-- * The file contains no statement that commits implicitly (no TRUNCATE, no
--   ALTER TABLE), so it can be loaded and rolled back as one transaction.
-- * Historical data uses fixed dates in September and October 2026. Live data
--   relative to NOW() is in the last section.

-- 1. Remove existing data (child tables first)

DELETE FROM DEGERLENDIRME;
DELETE FROM TESLIM_ALMA;
-- Refunds reference their charge, so they are removed first.
DELETE FROM ODEME_ISLEMI WHERE kaynak_islem_id IS NOT NULL;
DELETE FROM ODEME_ISLEMI;
DELETE FROM SIPARIS_KALEMI;
DELETE FROM SIPARIS;
DELETE FROM PAKET_ILANI;
DELETE FROM ISLETME;
DELETE FROM KULLANICI_ROL;
DELETE FROM KULLANICI;

-- 2. Users and roles

SET @parola_ozeti = '$2a$12$dhTY0Pl3r8RFcuArmaQ0ouLliZQRwzWC9R1SmZ6pFVmk5TGl72OB2';

INSERT INTO KULLANICI (ad, soyad, eposta, telefon, parola_ozeti, aktif_mi, kayit_zamani) VALUES
    ('Selin', 'Aydemir', 'selin.aydemir@ornek.test', '0532 410 00 01', @parola_ozeti, TRUE, '2026-08-01 09:00:00'),
    ('Mehmet', 'Karaca', 'mehmet.karaca@ornek.test', '0532 410 00 02', @parola_ozeti, TRUE, '2026-08-03 10:15:00'),
    ('Zeynep', 'Ertaş', 'zeynep.ertas@ornek.test', '0532 410 00 03', @parola_ozeti, TRUE, '2026-08-04 11:20:00'),
    ('Can', 'Doğan', 'can.dogan@ornek.test', '0532 410 00 04', @parola_ozeti, TRUE, '2026-08-05 12:40:00'),
    ('Elif', 'Şahin', 'elif.sahin@ornek.test', '0532 410 00 05', @parola_ozeti, TRUE, '2026-08-06 14:05:00'),
    ('Burak', 'Yıldız', 'burak.yildiz@ornek.test', '0532 410 00 06', @parola_ozeti, TRUE, '2026-08-07 15:30:00'),
    ('Ayten', 'Koç', 'ayten.koc@ornek.test', '0532 410 00 07', @parola_ozeti, TRUE, '2026-10-28 16:45:00'),
    ('Ahmet', 'Yılmaz', 'ahmet.yilmaz@ornek.test', '0533 520 00 01', @parola_ozeti, TRUE, '2026-08-10 18:00:00'),
    ('Ayşe', 'Demir', 'ayse.demir@ornek.test', '0533 520 00 02', @parola_ozeti, TRUE, '2026-08-11 19:10:00'),
    ('Fatma', 'Kaya', 'fatma.kaya@ornek.test', NULL, @parola_ozeti, TRUE, '2026-08-12 20:20:00'),
    ('Mustafa', 'Çelik', 'mustafa.celik@ornek.test', '0533 520 00 04', @parola_ozeti, TRUE, '2026-08-13 08:30:00'),
    ('Emine', 'Arslan', 'emine.arslan@ornek.test', '0533 520 00 05', @parola_ozeti, TRUE, '2026-08-14 09:40:00'),
    ('Hasan', 'Öztürk', 'hasan.ozturk@ornek.test', '0533 520 00 06', @parola_ozeti, TRUE, '2026-08-15 10:50:00'),
    ('Hatice', 'Kılıç', 'hatice.kilic@ornek.test', NULL, @parola_ozeti, TRUE, '2026-08-16 11:00:00'),
    ('Ali', 'Aksoy', 'ali.aksoy@ornek.test', '0533 520 00 08', @parola_ozeti, TRUE, '2026-08-17 12:10:00'),
    ('Zehra', 'Polat', 'zehra.polat@ornek.test', '0533 520 00 09', @parola_ozeti, TRUE, '2026-08-18 13:20:00'),
    ('Murat', 'Şen', 'murat.sen@ornek.test', '0533 520 00 10', @parola_ozeti, TRUE, '2026-08-19 14:30:00'),
    ('Esra', 'Güneş', 'esra.gunes@ornek.test', '0533 520 00 11', @parola_ozeti, TRUE, '2026-08-20 15:40:00'),
    ('Kemal', 'Tekin', 'kemal.tekin@ornek.test', NULL, @parola_ozeti, TRUE, '2026-08-21 16:50:00');

-- Every account is a customer, and further roles are granted on top.
INSERT INTO KULLANICI_ROL (kullanici_id, rol_id)
SELECT k.kullanici_id, r.rol_id
FROM KULLANICI k
JOIN ROL r ON r.rol_adi = 'MUSTERI'
WHERE k.eposta LIKE '%@ornek.test';

INSERT INTO KULLANICI_ROL (kullanici_id, rol_id)
SELECT k.kullanici_id, r.rol_id
FROM KULLANICI k
JOIN ROL r ON r.rol_adi = 'YONETICI'
WHERE k.eposta IN ('selin.aydemir@ornek.test');

INSERT INTO KULLANICI_ROL (kullanici_id, rol_id)
SELECT k.kullanici_id, r.rol_id
FROM KULLANICI k
JOIN ROL r ON r.rol_adi = 'ISLETME_YETKILISI'
WHERE k.eposta IN ('mehmet.karaca@ornek.test', 'zeynep.ertas@ornek.test', 'can.dogan@ornek.test', 'elif.sahin@ornek.test', 'burak.yildiz@ornek.test');

-- 3. Businesses
-- Mehmet Karaca runs two branches, and each branch is a separate business.
-- Koç Simit Fırını has applied and is still waiting for approval, so its
-- owner holds the MUSTERI role only.

INSERT INTO ISLETME (yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon, onay_durumu, aktif_mi) VALUES
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'), 'Karaca Fırını Kızılay Şubesi', 'FIRIN', 'Ankara', 'Çankaya', 'Meşrutiyet Cad. No: 12, Kızılay', '0312 555 10 01', 'ONAYLANDI', TRUE),
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'), 'Karaca Fırını Bahçelievler Şubesi', 'FIRIN', 'Ankara', 'Çankaya', 'Aşkabat Cad. No: 48, Bahçelievler', '0312 555 10 02', 'ONAYLANDI', TRUE),
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zeynep.ertas@ornek.test'), 'Ertaş Kafe', 'KAFE', 'Ankara', 'Keçiören', 'Kızlarpınarı Cad. No: 7', '0312 555 20 01', 'ONAYLANDI', TRUE),
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'), 'Moda Kahve Evi', 'KAFE', 'İstanbul', 'Kadıköy', 'Moda Cad. No: 85', '0216 555 30 01', 'ONAYLANDI', TRUE),
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'elif.sahin@ornek.test'), 'Şahin Ev Yemekleri', 'RESTORAN', 'İstanbul', 'Kadıköy', 'Bahariye Cad. No: 22', '0216 555 30 02', 'ONAYLANDI', TRUE),
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'burak.yildiz@ornek.test'), 'Beşiktaş Lokantası', 'RESTORAN', 'İstanbul', 'Beşiktaş', 'Ihlamurdere Cad. No: 31', '0212 555 40 01', 'ONAYLANDI', TRUE),
    ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ayten.koc@ornek.test'), 'Koç Simit Fırını', 'FIRIN', 'Ankara', 'Yenimahalle', 'Ragıp Tüzün Cad. No: 64', '0312 555 50 01', 'BEKLEMEDE', TRUE);

-- 4. Historical listings (September and October 2026)
-- Each pickup window lies within the listing's own day. The listings have
-- ended and are therefore PASIF. kalan_adet = toplam_adet minus the
-- quantities of orders that were not cancelled.

INSERT INTO PAKET_ILANI (isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat, satis_fiyati, toplam_adet,
                         kalan_adet, teslim_baslangic, teslim_bitis, durum, olusturulma_zamani) VALUES
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), 'Akşam Ekmek Paketi', 'Gün içinde pişmiş köy ekmeği ve tam buğday ekmeği.', 'Gluten içerir.', 80.00, 35.00, 6, 3,
     '2026-09-08 19:00:00', '2026-09-08 20:00:00', 'PASIF', '2026-09-08 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), 'Poğaça ve Simit Paketi', 'Peynirli poğaça, zeytinli poğaça ve simit.', 'Gluten, süt, susam içerir.', 90.00, 40.00, 5, 3,
     '2026-09-08 19:00:00', '2026-09-08 20:00:00', 'PASIF', '2026-09-08 12:00:00'),
    -- Report section 6.1 example: 5 units, 120.00 -> 60.00, 19:00-20:00.
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), 'Karışık Unlu Mamul Paketi', 'Ekmek, poğaça ve açma karışık paket.', 'Gluten, süt, yumurta içerir.', 120.00, 60.00, 5, 3,
     '2026-10-15 19:00:00', '2026-10-15 20:00:00', 'PASIF', '2026-10-15 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), 'Hafta Sonu Börek Paketi', 'Su böreği ve kol böreği dilimleri.', 'Gluten, süt, yumurta içerir.', 150.00, 70.00, 4, 0,
     '2026-10-03 18:00:00', '2026-10-03 19:00:00', 'PASIF', '2026-10-03 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi'), 'Akşam Ekmek Paketi', 'Somun ekmek ve çavdar ekmeği.', 'Gluten içerir.', 80.00, 35.00, 4, 2,
     '2026-09-12 19:00:00', '2026-09-12 20:00:00', 'PASIF', '2026-09-12 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi'), 'Kurabiye Paketi', 'Tuzlu ve tatlı kurabiye karışımı.', 'Gluten, süt, yumurta, fındık içerir.', 100.00, 45.00, 3, 2,
     '2026-10-10 18:30:00', '2026-10-10 19:30:00', 'PASIF', '2026-10-10 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe'), 'Sandviç ve Kek Paketi', 'Günün sandviçleri ve dilim kek.', 'Gluten, süt, yumurta içerir.', 140.00, 65.00, 6, 1,
     '2026-09-15 17:00:00', '2026-09-15 18:00:00', 'PASIF', '2026-09-15 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe'), 'Tatlı Paketi', 'Sütlaç, kazandibi ve günün tatlısı.', 'Süt, gluten içerir.', 160.00, 75.00, 3, 2,
     '2026-10-20 17:00:00', '2026-10-20 18:00:00', 'PASIF', '2026-10-20 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), 'Kruvasan Paketi', 'Sade, çikolatalı ve peynirli kruvasan.', 'Gluten, süt, yumurta içerir.', 130.00, 55.00, 8, 1,
     '2026-09-10 20:00:00', '2026-09-10 21:00:00', 'PASIF', '2026-09-10 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), 'Cheesecake Paketi', 'İki dilim frambuazlı cheesecake.', 'Süt, yumurta, gluten içerir.', 180.00, 80.00, 5, 3,
     '2026-09-24 20:00:00', '2026-09-24 21:00:00', 'PASIF', '2026-09-24 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), 'Kahvaltılık Paketi', 'Poğaça, peynir ve zeytin.', 'Gluten, süt içerir.', 150.00, 70.00, 6, 2,
     '2026-10-08 20:00:00', '2026-10-08 21:00:00', 'PASIF', '2026-10-08 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), 'Brownie Paketi', 'Cevizli brownie dilimleri.', 'Gluten, süt, yumurta, ceviz içerir.', 120.00, 50.00, 5, 2,
     '2026-10-22 20:00:00', '2026-10-22 21:00:00', 'PASIF', '2026-10-22 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), 'Günün Menüsü Paketi', 'Mercimek çorbası, etli nohut ve pilav.', 'Gluten içerebilir.', 200.00, 90.00, 3, 1,
     '2026-09-17 21:00:00', '2026-09-17 22:00:00', 'PASIF', '2026-09-17 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), 'Zeytinyağlı Paketi', 'Zeytinyağlı fasulye, enginar ve dolma.', 'Bilinen alerjen içermez.', 170.00, 75.00, 3, 1,
     '2026-10-14 21:00:00', '2026-10-14 22:00:00', 'PASIF', '2026-10-14 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), 'Akşam Yemeği Paketi', 'Izgara köfte, piyaz ve pilav.', 'Gluten içerir.', 220.00, 100.00, 4, 1,
     '2026-09-20 21:30:00', '2026-09-20 22:30:00', 'PASIF', '2026-09-20 12:00:00'),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), 'Pilav ve Kuru Fasulye Paketi', 'Kuru fasulye, pirinç pilavı ve turşu.', 'Bilinen alerjen içermez.', 180.00, 80.00, 4, 3,
     '2026-10-25 21:30:00', '2026-10-25 22:30:00', 'PASIF', '2026-10-25 12:00:00');

-- 5. Historical orders
-- Every order has exactly one successful charge equal to its item total and a
-- pickup record whose window is that of the listing. Delivered orders are
-- verified by the business owner within the window, and cancelled orders are
-- fully refunded against their charge.

-- O1: Ahmet Yılmaz, Karaca Fırını Kızılay Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ahmet.yilmaz@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-09-08 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Ekmek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Poğaça ve Simit Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o1-tahsilat', 'TAHSILAT', 110.00, 'BASARILI', '2026-09-08 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '231376', '2026-09-08 19:00:00', '2026-09-08 20:00:00', '2026-09-08 19:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 5, 'Ekmekler hâlâ sıcaktı, çok memnun kaldım.', '2026-09-08 21:15:00');

-- O2: Ayşe Demir, Karaca Fırını Kızılay Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ayse.demir@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-09-08 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Ekmek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o2-tahsilat', 'TAHSILAT', 35.00, 'BASARILI', '2026-09-08 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '239295', '2026-09-08 19:00:00', '2026-09-08 20:00:00', '2026-09-08 19:20:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));

-- O3: Fatma Kaya, Karaca Fırını Kızılay Şubesi, TESLIM_ALINMADI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'fatma.kaya@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-09-08 13:40:00', 'TESLIM_ALINMADI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Poğaça ve Simit Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o3-tahsilat', 'TAHSILAT', 40.00, 'BASARILI', '2026-09-08 13:41:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '247214', '2026-09-08 19:00:00', '2026-09-08 20:00:00', NULL, NULL);

-- O4: Mustafa Çelik, Karaca Fırını Kızılay Şubesi, IPTAL_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mustafa.celik@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-09-08 14:00:00', 'IPTAL_EDILDI', '2026-09-08 14:30:00');
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Ekmek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o4-tahsilat', 'TAHSILAT', 35.00, 'BASARILI', '2026-09-08 14:01:00');
SET @tahsilat = LAST_INSERT_ID();
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, @tahsilat, 'demo-o4-iade', 'IADE', 35.00, 'BASARILI', '2026-09-08 14:30:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '255133', '2026-09-08 19:00:00', '2026-09-08 20:00:00', NULL, NULL);

-- O5: Emine Arslan, Karaca Fırını Kızılay Şubesi, TESLIM_EDILDI
-- Report section 6.1 example: 2 units, 120.00 charged, code verified by the owner, rated.
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'emine.arslan@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-10-15 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Karışık Unlu Mamul Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o5-tahsilat', 'TAHSILAT', 120.00, 'BASARILI', '2026-10-15 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '263052', '2026-10-15 19:00:00', '2026-10-15 20:00:00', '2026-10-15 19:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 5, 'Paket açıklamadaki gibiydi, teslim hızlıydı.', '2026-10-15 21:15:00');

-- O6: Ahmet Yılmaz, Karaca Fırını Kızılay Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ahmet.yilmaz@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-10-03 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Hafta Sonu Börek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o6-tahsilat', 'TAHSILAT', 140.00, 'BASARILI', '2026-10-03 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '270971', '2026-10-03 18:00:00', '2026-10-03 19:00:00', '2026-10-03 18:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 4, 'Börekler lezzetliydi.', '2026-10-03 20:15:00');

-- O7: Zeynep Ertaş, Karaca Fırını Kızılay Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zeynep.ertas@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-10-03 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Hafta Sonu Börek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o7-tahsilat', 'TAHSILAT', 70.00, 'BASARILI', '2026-10-03 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '278890', '2026-10-03 18:00:00', '2026-10-03 19:00:00', '2026-10-03 18:20:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));

-- O8: Hasan Öztürk, Karaca Fırını Kızılay Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'hasan.ozturk@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), '2026-10-03 13:40:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Hafta Sonu Börek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o8-tahsilat', 'TAHSILAT', 70.00, 'BASARILI', '2026-10-03 13:41:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '286809', '2026-10-03 18:00:00', '2026-10-03 19:00:00', '2026-10-03 18:25:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));

-- O9: Ayşe Demir, Karaca Fırını Bahçelievler Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ayse.demir@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi'), '2026-09-12 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Ekmek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o9-tahsilat', 'TAHSILAT', 35.00, 'BASARILI', '2026-09-12 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '294728', '2026-09-12 19:00:00', '2026-09-12 20:00:00', '2026-09-12 19:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));

-- O10: Hatice Kılıç, Karaca Fırını Bahçelievler Şubesi, TESLIM_ALINMADI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'hatice.kilic@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi'), '2026-09-12 13:20:00', 'TESLIM_ALINMADI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Ekmek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o10-tahsilat', 'TAHSILAT', 35.00, 'BASARILI', '2026-09-12 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '302647', '2026-09-12 19:00:00', '2026-09-12 20:00:00', NULL, NULL);

-- O11: Ali Aksoy, Karaca Fırını Bahçelievler Şubesi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ali.aksoy@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi'), '2026-10-10 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Kurabiye Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o11-tahsilat', 'TAHSILAT', 45.00, 'BASARILI', '2026-10-10 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '310566', '2026-10-10 18:30:00', '2026-10-10 19:30:00', '2026-10-10 18:45:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mehmet.karaca@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 3, NULL, '2026-10-10 20:45:00');

-- O12: Zehra Polat, Ertaş Kafe, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zehra.polat@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe'), '2026-09-15 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Sandviç ve Kek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o12-tahsilat', 'TAHSILAT', 130.00, 'BASARILI', '2026-09-15 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '318485', '2026-09-15 17:00:00', '2026-09-15 18:00:00', '2026-09-15 17:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zeynep.ertas@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 4, 'Sandviçler tazeydi.', '2026-09-15 19:15:00');

-- O13: Mustafa Çelik, Ertaş Kafe, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'mustafa.celik@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe'), '2026-09-15 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 3, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Sandviç ve Kek Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o13-tahsilat', 'TAHSILAT', 195.00, 'BASARILI', '2026-09-15 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '326404', '2026-09-15 17:00:00', '2026-09-15 18:00:00', '2026-09-15 17:20:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zeynep.ertas@ornek.test'));

-- O14: Ahmet Yılmaz, Ertaş Kafe, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ahmet.yilmaz@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe'), '2026-10-20 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Tatlı Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o14-tahsilat', 'TAHSILAT', 75.00, 'BASARILI', '2026-10-20 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '334323', '2026-10-20 17:00:00', '2026-10-20 18:00:00', '2026-10-20 17:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zeynep.ertas@ornek.test'));

-- O15: Emine Arslan, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'emine.arslan@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-09-10 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 3, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Kruvasan Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o15-tahsilat', 'TAHSILAT', 165.00, 'BASARILI', '2026-09-10 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '342242', '2026-09-10 20:00:00', '2026-09-10 21:00:00', '2026-09-10 20:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 5, 'Kruvasanlar harikaydı.', '2026-09-10 22:15:00');

-- O16: Hasan Öztürk, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'hasan.ozturk@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-09-10 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Kruvasan Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o16-tahsilat', 'TAHSILAT', 110.00, 'BASARILI', '2026-09-10 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '350161', '2026-09-10 20:00:00', '2026-09-10 21:00:00', '2026-09-10 20:20:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));

-- O17: Murat Şen, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'murat.sen@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-09-10 13:40:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Kruvasan Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o17-tahsilat', 'TAHSILAT', 110.00, 'BASARILI', '2026-09-10 13:41:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '358080', '2026-09-10 20:00:00', '2026-09-10 21:00:00', '2026-09-10 20:25:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));

-- O18: Ayşe Demir, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ayse.demir@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-09-24 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Cheesecake Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o18-tahsilat', 'TAHSILAT', 160.00, 'BASARILI', '2026-09-24 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '365999', '2026-09-24 20:00:00', '2026-09-24 21:00:00', '2026-09-24 20:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 4, NULL, '2026-09-24 22:15:00');

-- O19: Fatma Kaya, Moda Kahve Evi, IPTAL_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'fatma.kaya@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-09-24 13:20:00', 'IPTAL_EDILDI', '2026-09-24 13:50:00');
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Cheesecake Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o19-tahsilat', 'TAHSILAT', 80.00, 'BASARILI', '2026-09-24 13:21:00');
SET @tahsilat = LAST_INSERT_ID();
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, @tahsilat, 'demo-o19-iade', 'IADE', 80.00, 'BASARILI', '2026-09-24 13:50:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '373918', '2026-09-24 20:00:00', '2026-09-24 21:00:00', NULL, NULL);

-- O20: Emine Arslan, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'emine.arslan@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-10-08 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Kahvaltılık Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o20-tahsilat', 'TAHSILAT', 140.00, 'BASARILI', '2026-10-08 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '381837', '2026-10-08 20:00:00', '2026-10-08 21:00:00', '2026-10-08 20:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));

-- O21: Zeynep Ertaş, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zeynep.ertas@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-10-08 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Kahvaltılık Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o21-tahsilat', 'TAHSILAT', 140.00, 'BASARILI', '2026-10-08 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '389756', '2026-10-08 20:00:00', '2026-10-08 21:00:00', '2026-10-08 20:20:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 5, 'Kahvaltı için birebir.', '2026-10-08 22:20:00');

-- O22: Hasan Öztürk, Moda Kahve Evi, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'hasan.ozturk@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-10-22 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Brownie Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o22-tahsilat', 'TAHSILAT', 100.00, 'BASARILI', '2026-10-22 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '397675', '2026-10-22 20:00:00', '2026-10-22 21:00:00', '2026-10-22 20:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'can.dogan@ornek.test'));

-- O23: Ali Aksoy, Moda Kahve Evi, TESLIM_ALINMADI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ali.aksoy@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), '2026-10-22 13:20:00', 'TESLIM_ALINMADI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Brownie Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o23-tahsilat', 'TAHSILAT', 50.00, 'BASARILI', '2026-10-22 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '405594', '2026-10-22 20:00:00', '2026-10-22 21:00:00', NULL, NULL);

-- O24: Esra Güneş, Şahin Ev Yemekleri, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'esra.gunes@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), '2026-09-17 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Günün Menüsü Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o24-tahsilat', 'TAHSILAT', 90.00, 'BASARILI', '2026-09-17 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '413513', '2026-09-17 21:00:00', '2026-09-17 22:00:00', '2026-09-17 21:15:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'elif.sahin@ornek.test'));

-- O25: Zehra Polat, Şahin Ev Yemekleri, TESLIM_ALINMADI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'zehra.polat@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), '2026-09-17 13:20:00', 'TESLIM_ALINMADI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Günün Menüsü Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o25-tahsilat', 'TAHSILAT', 90.00, 'BASARILI', '2026-09-17 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '421432', '2026-09-17 21:00:00', '2026-09-17 22:00:00', NULL, NULL);

-- O26: Kemal Tekin, Şahin Ev Yemekleri, TESLIM_ALINMADI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'kemal.tekin@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), '2026-10-14 13:00:00', 'TESLIM_ALINMADI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Zeytinyağlı Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o26-tahsilat', 'TAHSILAT', 75.00, 'BASARILI', '2026-10-14 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '429351', '2026-10-14 21:00:00', '2026-10-14 22:00:00', NULL, NULL);

-- O27: Emine Arslan, Şahin Ev Yemekleri, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'emine.arslan@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), '2026-10-14 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Zeytinyağlı Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o27-tahsilat', 'TAHSILAT', 75.00, 'BASARILI', '2026-10-14 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '437270', '2026-10-14 21:00:00', '2026-10-14 22:00:00', '2026-10-14 21:20:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'elif.sahin@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 2, 'Porsiyon beklediğimden küçüktü.', '2026-10-14 23:20:00');

-- O28: Hasan Öztürk, Beşiktaş Lokantası, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'hasan.ozturk@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), '2026-09-20 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 2, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Yemeği Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o28-tahsilat', 'TAHSILAT', 200.00, 'BASARILI', '2026-09-20 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '445189', '2026-09-20 21:30:00', '2026-09-20 22:30:00', '2026-09-20 21:45:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'burak.yildiz@ornek.test'));

-- O29: Ayşe Demir, Beşiktaş Lokantası, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ayse.demir@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), '2026-09-20 13:20:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Akşam Yemeği Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o29-tahsilat', 'TAHSILAT', 100.00, 'BASARILI', '2026-09-20 13:21:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '453108', '2026-09-20 21:30:00', '2026-09-20 22:30:00', '2026-09-20 21:50:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'burak.yildiz@ornek.test'));
INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
VALUES (@siparis, 4, NULL, '2026-09-20 23:50:00');

-- O30: Ahmet Yılmaz, Beşiktaş Lokantası, TESLIM_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ahmet.yilmaz@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), '2026-10-25 13:00:00', 'TESLIM_EDILDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Pilav ve Kuru Fasulye Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o30-tahsilat', 'TAHSILAT', 80.00, 'BASARILI', '2026-10-25 13:01:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '461027', '2026-10-25 21:30:00', '2026-10-25 22:30:00', '2026-10-25 21:45:00', (SELECT kullanici_id FROM KULLANICI WHERE eposta = 'burak.yildiz@ornek.test'));

-- O31: Fatma Kaya, Beşiktaş Lokantası, IPTAL_EDILDI
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'fatma.kaya@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), '2026-10-25 13:20:00', 'IPTAL_EDILDI', '2026-10-25 13:50:00');
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Pilav ve Kuru Fasulye Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-o31-tahsilat', 'TAHSILAT', 80.00, 'BASARILI', '2026-10-25 13:21:00');
SET @tahsilat = LAST_INSERT_ID();
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, @tahsilat, 'demo-o31-iade', 'IADE', 80.00, 'BASARILI', '2026-10-25 13:50:00');
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '468946', '2026-10-25 21:30:00', '2026-10-25 22:30:00', NULL, NULL);

-- 6. Live demo data (relative to NOW())
-- The rows below are valid for a few hours after loading: the pickup windows
-- open 3 hours after the file is applied and close 1 hour later, after which
-- the scheduled event marks the open order as TESLIM_ALINMADI. Reset the dev
-- database before a demo so that this section is loaded again (see README).

SET @canli_olusturma = NOW();
SET @canli_baslangic = @canli_olusturma + INTERVAL 3 HOUR;
SET @canli_bitis = @canli_olusturma + INTERVAL 4 HOUR;

-- One active listing per approved business.
INSERT INTO PAKET_ILANI (isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat, satis_fiyati, toplam_adet,
                         kalan_adet, teslim_baslangic, teslim_bitis, durum, olusturulma_zamani) VALUES
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Kızılay Şubesi'), 'Bugünün Ekmek Paketi', 'Günün ekmeklerinden karışık paket.', 'Gluten içerir.', 80.00, 35.00, 6, 6,
     @canli_baslangic, @canli_bitis, 'AKTIF', @canli_olusturma),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Karaca Fırını Bahçelievler Şubesi'), 'Bugünün Simit Paketi', 'Simit ve açma.', 'Gluten, susam içerir.', 70.00, 30.00, 5, 5,
     @canli_baslangic, @canli_bitis, 'AKTIF', @canli_olusturma),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Ertaş Kafe'), 'Bugünün Kek Paketi', 'Dilim kek ve muffin.', 'Gluten, süt, yumurta içerir.', 120.00, 55.00, 4, 4,
     @canli_baslangic, @canli_bitis, 'AKTIF', @canli_olusturma),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), 'Bugünün Kruvasan Paketi', 'Sade ve çikolatalı kruvasan.', 'Gluten, süt, yumurta içerir.', 130.00, 55.00, 6, 5,
     @canli_baslangic, @canli_bitis, 'AKTIF', @canli_olusturma),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Şahin Ev Yemekleri'), 'Bugünün Ev Yemeği Paketi', 'Günün tenceresinden iki çeşit.', 'Gluten içerebilir.', 190.00, 85.00, 3, 3,
     @canli_baslangic, @canli_bitis, 'AKTIF', @canli_olusturma),
    ((SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Beşiktaş Lokantası'), 'Bugünün Lokanta Paketi', 'Günün ana yemeği ve pilav.', 'Gluten içerir.', 210.00, 95.00, 3, 3,
     @canli_baslangic, @canli_bitis, 'AKTIF', @canli_olusturma);

-- A confirmed order waiting for pickup: Ayşe Demir, Moda Kahve Evi.
INSERT INTO SIPARIS (musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)
VALUES ((SELECT kullanici_id FROM KULLANICI WHERE eposta = 'ayse.demir@ornek.test'), (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi'), @canli_olusturma, 'ONAYLANDI', NULL);
SET @siparis = LAST_INSERT_ID();
INSERT INTO SIPARIS_KALEMI (siparis_id, ilan_id, adet, birim_fiyat)
SELECT @siparis, ilan_id, 1, satis_fiyati FROM PAKET_ILANI
WHERE baslik = 'Bugünün Kruvasan Paketi' AND isletme_id = (SELECT isletme_id FROM ISLETME WHERE isletme_adi = 'Moda Kahve Evi');
INSERT INTO ODEME_ISLEMI (siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)
VALUES (@siparis, NULL, 'demo-canli-tahsilat', 'TAHSILAT', 55.00, 'BASARILI', @canli_olusturma);
INSERT INTO TESLIM_ALMA (siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)
VALUES (@siparis, '476865', @canli_baslangic, @canli_bitis, NULL, NULL);
