-- Creates the ten tables of the end_of_day schema exactly as defined in the
-- project report, together with their primary key, unique, foreign key and
-- check constraints, and inserts the initial roles as reference data.
--
-- Character set and collation come from the database default
-- (utf8mb4 / utf8mb4_turkish_ci) unless a column states otherwise.
--
-- Status and type columns (ROL.rol_adi, ISLETME.isletme_turu,
-- ISLETME.onay_durumu, PAKET_ILANI.durum, SIPARIS.durum,
-- ODEME_ISLEMI.islem_turu, ODEME_ISLEMI.durum) use utf8mb4_bin: these columns
-- hold fixed codes that map one-to-one to Java enum constants, so comparisons
-- must be exact and case-sensitive.
--
-- Rules that span several tables are enforced by later migrations.

CREATE TABLE KULLANICI (
    kullanici_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ad VARCHAR(50) NOT NULL,
    soyad VARCHAR(50) NOT NULL,
    -- In utf8mb4_turkish_ci the capital I matches the dotless small ı, not the
    -- small i, so 'ALI@ORNEK.COM' and 'ali@ornek.com' would be two different
    -- values. Email addresses are not Turkish text: their uniqueness must
    -- ignore letter case in the standard way, which utf8mb4_0900_as_ci does.
    eposta VARCHAR(254) COLLATE utf8mb4_0900_as_ci NOT NULL,
    telefon VARCHAR(20) NULL,
    parola_ozeti VARCHAR(100) NOT NULL,
    aktif_mi BOOLEAN NOT NULL DEFAULT TRUE,
    kayit_zamani DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_kullanici PRIMARY KEY (kullanici_id),
    CONSTRAINT uq_kullanici_eposta UNIQUE (eposta)
) ENGINE = InnoDB;

CREATE TABLE ROL (
    rol_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rol_adi VARCHAR(30) COLLATE utf8mb4_bin NOT NULL,
    CONSTRAINT pk_rol PRIMARY KEY (rol_id),
    CONSTRAINT uq_rol_rol_adi UNIQUE (rol_adi)
) ENGINE = InnoDB;

CREATE TABLE KULLANICI_ROL (
    kullanici_id BIGINT UNSIGNED NOT NULL,
    rol_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_kullanici_rol PRIMARY KEY (kullanici_id, rol_id),
    CONSTRAINT fk_kullanici_rol_kullanici_id FOREIGN KEY (kullanici_id)
        REFERENCES KULLANICI (kullanici_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_kullanici_rol_rol_id FOREIGN KEY (rol_id)
        REFERENCES ROL (rol_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE ISLETME (
    isletme_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    yetkili_id BIGINT UNSIGNED NOT NULL,
    isletme_adi VARCHAR(100) NOT NULL,
    isletme_turu VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    sehir VARCHAR(50) NOT NULL,
    ilce VARCHAR(50) NOT NULL,
    adres VARCHAR(255) NOT NULL,
    telefon VARCHAR(20) NOT NULL,
    onay_durumu VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'BEKLEMEDE',
    aktif_mi BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_isletme PRIMARY KEY (isletme_id),
    CONSTRAINT fk_isletme_yetkili_id FOREIGN KEY (yetkili_id)
        REFERENCES KULLANICI (kullanici_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_isletme_isletme_turu CHECK (isletme_turu IN ('FIRIN', 'KAFE', 'RESTORAN')),
    CONSTRAINT chk_isletme_onay_durumu CHECK (onay_durumu IN ('BEKLEMEDE', 'ONAYLANDI', 'REDDEDILDI'))
) ENGINE = InnoDB;

CREATE TABLE PAKET_ILANI (
    ilan_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    isletme_id BIGINT UNSIGNED NOT NULL,
    baslik VARCHAR(100) NOT NULL,
    aciklama TEXT NOT NULL,
    -- Never left empty: when there is no allergen information the business
    -- states that explicitly.
    alerjen_bilgisi VARCHAR(500) NOT NULL,
    normal_fiyat DECIMAL(10, 2) NOT NULL,
    satis_fiyati DECIMAL(10, 2) NOT NULL,
    toplam_adet INT UNSIGNED NOT NULL,
    kalan_adet INT UNSIGNED NOT NULL,
    teslim_baslangic DATETIME NOT NULL,
    teslim_bitis DATETIME NOT NULL,
    durum VARCHAR(10) COLLATE utf8mb4_bin NOT NULL DEFAULT 'AKTIF',
    olusturulma_zamani DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_paket_ilani PRIMARY KEY (ilan_id),
    CONSTRAINT fk_paket_ilani_isletme_id FOREIGN KEY (isletme_id)
        REFERENCES ISLETME (isletme_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_paket_ilani_aciklama_not_blank CHECK (CHAR_LENGTH(TRIM(aciklama)) > 0),
    CONSTRAINT chk_paket_ilani_alerjen_bilgisi_not_blank CHECK (CHAR_LENGTH(TRIM(alerjen_bilgisi)) > 0),
    CONSTRAINT chk_paket_ilani_toplam_adet_positive CHECK (toplam_adet > 0),
    CONSTRAINT chk_paket_ilani_kalan_adet_within_total CHECK (kalan_adet <= toplam_adet),
    CONSTRAINT chk_paket_ilani_satis_fiyati_discounted CHECK (satis_fiyati > 0 AND satis_fiyati < normal_fiyat),
    CONSTRAINT chk_paket_ilani_teslim_window CHECK (teslim_baslangic < teslim_bitis),
    CONSTRAINT chk_paket_ilani_durum CHECK (durum IN ('AKTIF', 'PASIF'))
) ENGINE = InnoDB;

CREATE TABLE SIPARIS (
    siparis_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    musteri_id BIGINT UNSIGNED NOT NULL,
    isletme_id BIGINT UNSIGNED NOT NULL,
    olusturulma_zamani DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    durum VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ONAYLANDI',
    iptal_zamani DATETIME NULL,
    CONSTRAINT pk_siparis PRIMARY KEY (siparis_id),
    CONSTRAINT fk_siparis_musteri_id FOREIGN KEY (musteri_id)
        REFERENCES KULLANICI (kullanici_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_siparis_isletme_id FOREIGN KEY (isletme_id)
        REFERENCES ISLETME (isletme_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_siparis_durum CHECK (durum IN ('ONAYLANDI', 'TESLIM_EDILDI', 'IPTAL_EDILDI', 'TESLIM_ALINMADI')),
    -- A cancellation time is recorded exactly when the order is cancelled.
    CONSTRAINT chk_siparis_iptal_zamani CHECK ((durum = 'IPTAL_EDILDI') = (iptal_zamani IS NOT NULL))
) ENGINE = InnoDB;

CREATE TABLE SIPARIS_KALEMI (
    siparis_id BIGINT UNSIGNED NOT NULL,
    ilan_id BIGINT UNSIGNED NOT NULL,
    adet INT UNSIGNED NOT NULL,
    birim_fiyat DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_siparis_kalemi PRIMARY KEY (siparis_id, ilan_id),
    CONSTRAINT fk_siparis_kalemi_siparis_id FOREIGN KEY (siparis_id)
        REFERENCES SIPARIS (siparis_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_siparis_kalemi_ilan_id FOREIGN KEY (ilan_id)
        REFERENCES PAKET_ILANI (ilan_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_siparis_kalemi_adet_positive CHECK (adet > 0),
    CONSTRAINT chk_siparis_kalemi_birim_fiyat_positive CHECK (birim_fiyat > 0)
) ENGINE = InnoDB;

CREATE TABLE ODEME_ISLEMI (
    islem_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    siparis_id BIGINT UNSIGNED NOT NULL,
    kaynak_islem_id BIGINT UNSIGNED NULL,
    istek_no VARCHAR(64) NOT NULL,
    islem_turu VARCHAR(10) COLLATE utf8mb4_bin NOT NULL,
    tutar DECIMAL(10, 2) NOT NULL,
    durum VARCHAR(10) COLLATE utf8mb4_bin NOT NULL,
    islem_zamani DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_odeme_islemi PRIMARY KEY (islem_id),
    CONSTRAINT uq_odeme_islemi_istek_no UNIQUE (istek_no),
    CONSTRAINT fk_odeme_islemi_siparis_id FOREIGN KEY (siparis_id)
        REFERENCES SIPARIS (siparis_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_odeme_islemi_kaynak_islem_id FOREIGN KEY (kaynak_islem_id)
        REFERENCES ODEME_ISLEMI (islem_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_odeme_islemi_islem_turu CHECK (islem_turu IN ('TAHSILAT', 'IADE')),
    CONSTRAINT chk_odeme_islemi_tutar_positive CHECK (tutar > 0),
    CONSTRAINT chk_odeme_islemi_durum CHECK (durum IN ('BASARILI', 'BASARISIZ')),
    -- A charge stands on its own; a refund always points to the charge it reverses.
    CONSTRAINT chk_odeme_islemi_kaynak_islem CHECK (
        (islem_turu = 'TAHSILAT' AND kaynak_islem_id IS NULL)
        OR (islem_turu = 'IADE' AND kaynak_islem_id IS NOT NULL))
) ENGINE = InnoDB;

CREATE TABLE TESLIM_ALMA (
    siparis_id BIGINT UNSIGNED NOT NULL,
    teslim_kodu CHAR(6) NOT NULL,
    teslim_baslangic DATETIME NOT NULL,
    teslim_bitis DATETIME NOT NULL,
    teslim_zamani DATETIME NULL,
    dogrulayan_id BIGINT UNSIGNED NULL,
    CONSTRAINT pk_teslim_alma PRIMARY KEY (siparis_id),
    CONSTRAINT uq_teslim_alma_teslim_kodu UNIQUE (teslim_kodu),
    CONSTRAINT fk_teslim_alma_siparis_id FOREIGN KEY (siparis_id)
        REFERENCES SIPARIS (siparis_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_teslim_alma_dogrulayan_id FOREIGN KEY (dogrulayan_id)
        REFERENCES KULLANICI (kullanici_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_teslim_alma_teslim_window CHECK (teslim_baslangic < teslim_bitis),
    CONSTRAINT chk_teslim_alma_teslim_kodu_format CHECK (teslim_kodu REGEXP '^[0-9]{6}$'),
    -- The pickup time and the verifying user are recorded together.
    CONSTRAINT chk_teslim_alma_verification CHECK ((teslim_zamani IS NULL) = (dogrulayan_id IS NULL))
) ENGINE = InnoDB;

CREATE TABLE DEGERLENDIRME (
    siparis_id BIGINT UNSIGNED NOT NULL,
    puan TINYINT UNSIGNED NOT NULL,
    yorum VARCHAR(1000) NULL,
    olusturulma_zamani DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_degerlendirme PRIMARY KEY (siparis_id),
    CONSTRAINT fk_degerlendirme_siparis_id FOREIGN KEY (siparis_id)
        REFERENCES SIPARIS (siparis_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_degerlendirme_puan_range CHECK (puan BETWEEN 1 AND 5)
) ENGINE = InnoDB;

-- Reference data: the roles the application knows about.
INSERT INTO ROL (rol_adi) VALUES
    ('MUSTERI'),
    ('ISLETME_YETKILISI'),
    ('YONETICI');
