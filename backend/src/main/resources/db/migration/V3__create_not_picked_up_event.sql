-- Marks confirmed orders whose pickup window has ended as not picked up
-- (project report, section 5.1).
--
-- Stock is not returned to the listing and no refund is created: an order
-- that was not picked up stays paid. Unlike the triggers, the procedure
-- compares with the current time, because that is its purpose.
--
-- The event runs every five minutes; event_scheduler is ON on the server.

DELIMITER $$

CREATE PROCEDURE proc_mark_not_picked_up()
BEGIN
    UPDATE SIPARIS s
    JOIN TESLIM_ALMA t ON t.siparis_id = s.siparis_id
    SET s.durum = 'TESLIM_ALINMADI'
    WHERE s.durum = 'ONAYLANDI'
      AND t.teslim_bitis < NOW();
END$$

DELIMITER ;

CREATE EVENT evt_mark_not_picked_up
    ON SCHEDULE EVERY 5 MINUTE
    ON COMPLETION PRESERVE
    ENABLE
    DO CALL proc_mark_not_picked_up();
