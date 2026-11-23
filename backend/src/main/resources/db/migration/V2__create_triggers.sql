-- Enforces the cross-table business rules of the project report (section 5.1)
-- with BEFORE INSERT / BEFORE UPDATE triggers. Each table has at most one
-- trigger per event; when a trigger covers several rules they are checked in
-- the order listed in its comment.
--
-- A violation raises SQLSTATE '45000' with a message of the form
-- 'EOD-0xx: <Turkish message>'; the codes are listed in docs/error-codes.md.
--
-- Design rules shared by all triggers:
-- * No trigger compares a value with the current time. Time-based rules live
--   in the backend, so that historical data can be bulk-loaded.
-- * A trigger judges a row only when the rows it refers to exist. A missing
--   reference or a NULL value is left to the foreign key and NOT NULL
--   constraints, which MySQL checks after BEFORE triggers and which report it
--   with their own error codes.
-- * Payment rules first lock the related SIPARIS row with SELECT ... FOR
--   UPDATE, so that concurrent payment transactions of the same order are
--   serialised, and then read existing payments with locking reads, which
--   always see the latest committed rows regardless of the transaction
--   snapshot. The rules therefore hold independently of the application.

DELIMITER $$

-- SIPARIS_KALEMI
-- EOD-001: the listing of an order item belongs to the business of the order.

CREATE TRIGGER trg_siparis_kalemi_before_insert
BEFORE INSERT ON SIPARIS_KALEMI
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1
               FROM SIPARIS s
               JOIN PAKET_ILANI p ON p.ilan_id = NEW.ilan_id
               WHERE s.siparis_id = NEW.siparis_id
                 AND p.isletme_id <> s.isletme_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-001: Sipariş kalemindeki ilan, siparişin işletmesine ait değil.';
    END IF;
END$$

CREATE TRIGGER trg_siparis_kalemi_before_update
BEFORE UPDATE ON SIPARIS_KALEMI
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1
               FROM SIPARIS s
               JOIN PAKET_ILANI p ON p.ilan_id = NEW.ilan_id
               WHERE s.siparis_id = NEW.siparis_id
                 AND p.isletme_id <> s.isletme_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-001: Sipariş kalemindeki ilan, siparişin işletmesine ait değil.';
    END IF;
END$$

-- SIPARIS
-- EOD-001: the business of an order that already has items cannot change.
-- EOD-005: a status change must start at ONAYLANDI and end in one of the final
--          states TESLIM_EDILDI, IPTAL_EDILDI or TESLIM_ALINMADI.
--
-- There is no INSERT trigger: transition rules describe how an existing order
-- moves between states, so they apply to updates only. Inserts may use any
-- valid status, which allows historical orders that are already in a final
-- state to be bulk-loaded.

CREATE TRIGGER trg_siparis_before_update
BEFORE UPDATE ON SIPARIS
FOR EACH ROW
BEGIN
    IF NEW.isletme_id <> OLD.isletme_id
       AND EXISTS (SELECT 1 FROM SIPARIS_KALEMI k WHERE k.siparis_id = OLD.siparis_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-001: Kalemi olan bir siparişin işletmesi değiştirilemez.';
    END IF;

    IF NEW.durum <> OLD.durum
       AND NOT (OLD.durum = 'ONAYLANDI'
                AND NEW.durum IN ('TESLIM_EDILDI', 'IPTAL_EDILDI', 'TESLIM_ALINMADI')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-005: Geçersiz sipariş durum geçişi.';
    END IF;
END$$

-- ODEME_ISLEMI
-- EOD-002: an order has at most one successful charge.
-- EOD-003: a refund refers to a successful charge of the same order.
-- EOD-004: successful refunds never exceed the amount of their charge.
--          Failed refunds are not counted.

CREATE TRIGGER trg_odeme_islemi_before_insert
BEFORE INSERT ON ODEME_ISLEMI
FOR EACH ROW
BEGIN
    DECLARE v_locked INT;
    DECLARE v_count INT;
    DECLARE v_charge_amount DECIMAL(10, 2);
    DECLARE v_refunded DECIMAL(12, 2);

    SELECT COUNT(*) INTO v_locked
    FROM SIPARIS
    WHERE siparis_id = NEW.siparis_id
    FOR UPDATE;

    IF NEW.islem_turu = 'TAHSILAT' AND NEW.durum = 'BASARILI' THEN
        SELECT COUNT(*) INTO v_count
        FROM ODEME_ISLEMI
        WHERE siparis_id = NEW.siparis_id
          AND islem_turu = 'TAHSILAT'
          AND durum = 'BASARILI'
        FOR SHARE;

        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'EOD-002: Bir siparişte birden fazla başarılı tahsilat olamaz.';
        END IF;
    END IF;

    IF NEW.islem_turu = 'IADE' AND NEW.kaynak_islem_id IS NOT NULL THEN
        IF EXISTS (SELECT 1
                   FROM ODEME_ISLEMI src
                   WHERE src.islem_id = NEW.kaynak_islem_id
                     AND NOT (src.siparis_id = NEW.siparis_id
                              AND src.islem_turu = 'TAHSILAT'
                              AND src.durum = 'BASARILI')) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'EOD-003: İade, aynı siparişin başarılı bir tahsilatına bağlanmalı.';
        END IF;

        IF NEW.durum = 'BASARILI' THEN
            SELECT MAX(tutar) INTO v_charge_amount
            FROM ODEME_ISLEMI
            WHERE islem_id = NEW.kaynak_islem_id
            FOR SHARE;

            SELECT COALESCE(SUM(tutar), 0) INTO v_refunded
            FROM ODEME_ISLEMI
            WHERE kaynak_islem_id = NEW.kaynak_islem_id
              AND islem_turu = 'IADE'
              AND durum = 'BASARILI'
            FOR SHARE;

            IF v_charge_amount IS NOT NULL AND v_refunded + NEW.tutar > v_charge_amount THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'EOD-004: Başarılı iadelerin toplamı tahsilat tutarını aşamaz.';
            END IF;
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_odeme_islemi_before_update
BEFORE UPDATE ON ODEME_ISLEMI
FOR EACH ROW
BEGIN
    DECLARE v_locked INT;
    DECLARE v_count INT;
    DECLARE v_charge_amount DECIMAL(10, 2);
    DECLARE v_refunded DECIMAL(12, 2);

    SELECT COUNT(*) INTO v_locked
    FROM SIPARIS
    WHERE siparis_id = NEW.siparis_id
    FOR UPDATE;

    -- Covers a failed charge that is turned into a successful one.
    IF NEW.islem_turu = 'TAHSILAT' AND NEW.durum = 'BASARILI' THEN
        SELECT COUNT(*) INTO v_count
        FROM ODEME_ISLEMI
        WHERE siparis_id = NEW.siparis_id
          AND islem_turu = 'TAHSILAT'
          AND durum = 'BASARILI'
          AND islem_id <> OLD.islem_id
        FOR SHARE;

        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'EOD-002: Bir siparişte birden fazla başarılı tahsilat olamaz.';
        END IF;
    END IF;

    IF NEW.islem_turu = 'IADE' AND NEW.kaynak_islem_id IS NOT NULL THEN
        IF EXISTS (SELECT 1
                   FROM ODEME_ISLEMI src
                   WHERE src.islem_id = NEW.kaynak_islem_id
                     AND NOT (src.siparis_id = NEW.siparis_id
                              AND src.islem_turu = 'TAHSILAT'
                              AND src.durum = 'BASARILI')) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'EOD-003: İade, aynı siparişin başarılı bir tahsilatına bağlanmalı.';
        END IF;

        -- Covers a failed refund that is turned into a successful one and a
        -- change of the amount of a successful refund.
        IF NEW.durum = 'BASARILI' THEN
            SELECT MAX(tutar) INTO v_charge_amount
            FROM ODEME_ISLEMI
            WHERE islem_id = NEW.kaynak_islem_id
            FOR SHARE;

            SELECT COALESCE(SUM(tutar), 0) INTO v_refunded
            FROM ODEME_ISLEMI
            WHERE kaynak_islem_id = NEW.kaynak_islem_id
              AND islem_turu = 'IADE'
              AND durum = 'BASARILI'
              AND islem_id <> OLD.islem_id
            FOR SHARE;

            IF v_charge_amount IS NOT NULL AND v_refunded + NEW.tutar > v_charge_amount THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'EOD-004: Başarılı iadelerin toplamı tahsilat tutarını aşamaz.';
            END IF;
        END IF;
    END IF;
END$$

-- DEGERLENDIRME
-- EOD-006: only a delivered order can be reviewed.

CREATE TRIGGER trg_degerlendirme_before_insert
BEFORE INSERT ON DEGERLENDIRME
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1
               FROM SIPARIS s
               WHERE s.siparis_id = NEW.siparis_id
                 AND s.durum <> 'TESLIM_EDILDI') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-006: Yalnızca teslim edilmiş sipariş değerlendirilebilir.';
    END IF;
END$$

-- PAKET_ILANI
-- EOD-007: a listing is published (durum AKTIF) only for an approved and
--          active business. Updates are checked only when the status changes
--          to AKTIF, so that routine updates such as decreasing kalan_adet keep
--          working after the business has been deactivated.

CREATE TRIGGER trg_paket_ilani_before_insert
BEFORE INSERT ON PAKET_ILANI
FOR EACH ROW
BEGIN
    IF NEW.durum = 'AKTIF'
       AND EXISTS (SELECT 1
                   FROM ISLETME i
                   WHERE i.isletme_id = NEW.isletme_id
                     AND NOT (i.onay_durumu = 'ONAYLANDI' AND i.aktif_mi)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-007: İlan yalnızca onaylı ve aktif bir işletme için yayına alınabilir.';
    END IF;
END$$

CREATE TRIGGER trg_paket_ilani_before_update
BEFORE UPDATE ON PAKET_ILANI
FOR EACH ROW
BEGIN
    IF NEW.durum = 'AKTIF' AND OLD.durum <> 'AKTIF'
       AND EXISTS (SELECT 1
                   FROM ISLETME i
                   WHERE i.isletme_id = NEW.isletme_id
                     AND NOT (i.onay_durumu = 'ONAYLANDI' AND i.aktif_mi)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-007: İlan yalnızca onaylı ve aktif bir işletme için yayına alınabilir.';
    END IF;
END$$

-- TESLIM_ALMA
-- EOD-008: only the owner of the order's business verifies the pickup.

CREATE TRIGGER trg_teslim_alma_before_insert
BEFORE INSERT ON TESLIM_ALMA
FOR EACH ROW
BEGIN
    IF NEW.dogrulayan_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM KULLANICI u WHERE u.kullanici_id = NEW.dogrulayan_id)
       AND EXISTS (SELECT 1
                   FROM SIPARIS s
                   JOIN ISLETME i ON i.isletme_id = s.isletme_id
                   WHERE s.siparis_id = NEW.siparis_id
                     AND i.yetkili_id <> NEW.dogrulayan_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-008: Teslimi yalnızca siparişin işletmesinin yetkilisi doğrulayabilir.';
    END IF;
END$$

CREATE TRIGGER trg_teslim_alma_before_update
BEFORE UPDATE ON TESLIM_ALMA
FOR EACH ROW
BEGIN
    IF NEW.dogrulayan_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM KULLANICI u WHERE u.kullanici_id = NEW.dogrulayan_id)
       AND EXISTS (SELECT 1
                   FROM SIPARIS s
                   JOIN ISLETME i ON i.isletme_id = s.isletme_id
                   WHERE s.siparis_id = NEW.siparis_id
                     AND i.yetkili_id <> NEW.dogrulayan_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EOD-008: Teslimi yalnızca siparişin işletmesinin yetkilisi doğrulayabilir.';
    END IF;
END$$

DELIMITER ;
