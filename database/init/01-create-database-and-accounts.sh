#!/usr/bin/env bash
#
# Creates the end_of_day database and its three accounts.
#
# The MySQL image runs this script only when the data volume is created for the
# first time; it is skipped on every later start. To run it again, the volume
# must be removed (docker compose down -v), which deletes all data.
#
# Accounts:
#   eod_owner  - owns the schema and runs Flyway migrations (all privileges on
#                end_of_day, with GRANT OPTION)
#   eod_app    - runtime account of the application (SELECT, INSERT, UPDATE,
#                EXECUTE; no DDL and no GRANT)
#   eod_report - read-only reporting account, limited to views
set -euo pipefail

missing=()
for var in MYSQL_ROOT_PASSWORD EOD_OWNER_PASSWORD EOD_APP_PASSWORD EOD_REPORT_PASSWORD; do
    if [[ -z "${!var:-}" ]]; then
        missing+=("$var")
    fi
done
if (( ${#missing[@]} > 0 )); then
    echo "01-create-database-and-accounts.sh: missing environment variables: ${missing[*]}" >&2
    exit 1
fi

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket --user=root <<SQL
CREATE DATABASE end_of_day CHARACTER SET utf8mb4 COLLATE utf8mb4_turkish_ci;

CREATE USER 'eod_owner'@'%' IDENTIFIED BY '${EOD_OWNER_PASSWORD}';
CREATE USER 'eod_app'@'%' IDENTIFIED BY '${EOD_APP_PASSWORD}';
CREATE USER 'eod_report'@'%' IDENTIFIED BY '${EOD_REPORT_PASSWORD}';

GRANT ALL PRIVILEGES ON end_of_day.* TO 'eod_owner'@'%' WITH GRANT OPTION;

GRANT SELECT, INSERT, UPDATE, EXECUTE ON end_of_day.* TO 'eod_app'@'%';

-- eod_report can only connect for now. SELECT privileges on the reporting
-- views are granted by a Flyway migration once the views exist.
SQL
