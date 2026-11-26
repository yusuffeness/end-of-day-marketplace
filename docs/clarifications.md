# Clarifications

This file records details the project report left open and how they were resolved; none of them changes a table, column or rule defined in the report.

| Report section | Topic | Resolution |
| --- | --- | --- |
| 7.1 | Technology versions | Java 25 LTS, Spring Boot 4.1, MySQL 8.4 LTS, Maven Wrapper; versions pinned in the repository. |
| 7.1 | Data access | MySQL Connector/J over JDBC through Spring JdbcClient with named parameters; no ORM. |
| 7.3 | Database accounts | eod_owner (schema owner, runs migrations), eod_app (application: SELECT, INSERT, UPDATE, EXECUTE), eod_report (read-only, views only). |
| 7.3 | Database name and encoding | end_of_day, utf8mb4 with utf8mb4_turkish_ci, server time zone +03:00. |
| 7.3 | Password hashing | BCrypt with cost factor 12. |
| 8.1 | Bulk loading | Python generator writes CSV files loaded with LOAD DATA LOCAL INFILE. |
| 3.4 | ISLETME.onay_durumu values | BEKLEMEDE, ONAYLANDI, REDDEDILDI; default BEKLEMEDE. |
| 3.1 | Email uniqueness | eposta column uses utf8mb4_0900_as_ci so case differences (including I/i) never create duplicate accounts; no column added or renamed. |
| 3.2 | Initial roles | MUSTERI, ISLETME_YETKILISI, YONETICI inserted by the first migration as reference data. |
| 3, 4 | Text lengths | column lengths chosen as listed in V1__create_schema.sql; the report fixed names and types only. |
| 4.4 | Pickup code format | six digits. |
| 3, 4 | Code-valued columns | status and type columns use utf8mb4_bin so only the exact uppercase codes from the report are accepted. |
| 5.1 | Marking orders as not picked up | a database event runs every 5 minutes and marks confirmed orders whose pickup window has ended; stock and refunds are untouched. |
| 5.1 | Status transition rule on inserts | transition rules apply to updates only so historical data in final states can be bulk-loaded. |
| 7.3 | Report views | v_order_summary, v_business_sales, v_payment_summary; no personal fields. |
| 7.3 | Application delete privilege | only on KULLANICI_ROL, for revoking roles; all other records are deactivated, never deleted (report 5.2). |
| 7.3 | Password policy | 8 to 72 characters, at most 72 bytes in UTF-8 (BCrypt ignores everything after the 72nd byte), at least one letter and one digit. |
| 3.1 | Email normalization | addresses are trimmed and lower-cased with Locale.ROOT before they are stored or looked up. |
| 7.3 | Session contents | the session stores only the user id; the active flag and roles are read from the database on every request, so deactivation and role revocation apply immediately. |
| 7.3 | Sign-in of an inactive account | the active flag is checked after the password, so only someone who knows the password learns that the account is inactive. |
| 3.3, 6 | Registration | creates an active account with the role MUSTERI and does not sign the user in. |
| 3.5, 6 | Purchasable listings | the listing search shows AKTIF listings with stock left, of approved and active businesses, whose pickup window has not started; an order is accepted only before the window starts, so a listing in its window is no longer offered. |
| 4.4, 5.1 | Window boundaries | an order and a cancellation are accepted strictly before the window start; a pickup is verified from the window start to the window end, both included, matching the not-picked-up event (teslim_bitis < NOW()). |
| 1.3, 4.3 | Simulated payment | the request flags simulatePaymentFailure (POST /orders) and simulateRefundFailure (POST /orders/{id}/cancel) make the provider decline; a declined charge rolls the order back without a trace, a declined refund is stored as BASARISIZ and the order stays confirmed. |
| 4.3, 7.2 | Repeated requests | the request id is ODEME_ISLEMI.istek_no of the charge or the refund; a repeated request returns the result of the first one, a request id is never reused for another customer, order or kind of transaction. |
| 7.2 | Transaction isolation | order, cancellation and pickup transactions run under READ COMMITTED: they lock the rows they change first (business shared, listings exclusive in id order, order row exclusive), and later reads see the latest committed data without gap locks. |
| 4.4 | Pickup codes | six random digits from SecureRandom; a code that is taken is replaced by a new one. Codes are unique across all orders and are never logged. |
| 6 | Business owner as customer | an owner may order from their own business; the report does not forbid it. |
| 6, 7.3 | Visibility of orders and listings | another customer's order answers EOD-404, so its existence is not revealed; an inactive listing is shown only to the owner of its business and to administrators. |

Later entries are added when a detail is decided.
