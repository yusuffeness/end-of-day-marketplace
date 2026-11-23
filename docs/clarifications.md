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

Later entries are added when a detail is decided.
