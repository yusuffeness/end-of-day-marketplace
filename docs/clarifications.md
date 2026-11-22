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

Later entries are added when a detail is decided.
