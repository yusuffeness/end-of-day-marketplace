-- Table-level privileges, granted by eod_owner (which holds GRANT OPTION on
-- end_of_day). Database-level privileges are created by
-- database/init/01-create-database-and-accounts.sh.
--
-- eod_app keeps SELECT, INSERT, UPDATE and EXECUTE on the whole database and
-- gets DELETE on KULLANICI_ROL only, so that a role can be revoked. All other
-- records are deactivated, never deleted (project report, section 5.2).
--
-- eod_report reads the reporting views only and has no privilege on any base
-- table.

GRANT DELETE ON KULLANICI_ROL TO 'eod_app'@'%';

GRANT SELECT ON v_order_summary TO 'eod_report'@'%';
GRANT SELECT ON v_business_sales TO 'eod_report'@'%';
GRANT SELECT ON v_payment_summary TO 'eod_report'@'%';
