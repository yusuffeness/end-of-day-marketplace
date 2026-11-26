"""Load the generated CSV files into the perf database and run the checks of verify.sql.

The perf database is the separate MySQL server of the compose profile "perf"
(docker compose --profile perf up -d), published on MYSQL_PERF_PORT (default 3308). Its schema
is created by Flyway when the backend starts once with the perf profile against it. The dev
database and its demo data are never touched.

The files are loaded with LOAD DATA LOCAL INFILE as eod_owner, in foreign key order, with every
trigger active. --reset empties the data tables first (ROL and the Flyway history stay), so the
load can be repeated. Passwords are read from the repository .env file, never from the code.
"""

import argparse
import os
import re
import sys
import time
from pathlib import Path

import mysql.connector

ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent

# Load order follows the foreign keys; the column list maps every CSV column.
TABLES = [
    (
        "KULLANICI",
        "(kullanici_id, ad, soyad, eposta, telefon, parola_ozeti, aktif_mi, kayit_zamani)",
    ),
    (
        "KULLANICI_ROL",
        "(kullanici_id, @rol_adi) "
        "SET rol_id = (SELECT r.rol_id FROM ROL r WHERE r.rol_adi = @rol_adi)",
    ),
    (
        "ISLETME",
        "(isletme_id, yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon, "
        "onay_durumu, aktif_mi)",
    ),
    (
        "PAKET_ILANI",
        "(ilan_id, isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat, "
        "satis_fiyati, toplam_adet, kalan_adet, teslim_baslangic, teslim_bitis, durum, "
        "olusturulma_zamani)",
    ),
    ("SIPARIS", "(siparis_id, musteri_id, isletme_id, olusturulma_zamani, durum, iptal_zamani)"),
    ("SIPARIS_KALEMI", "(siparis_id, ilan_id, adet, birim_fiyat)"),
    (
        "ODEME_ISLEMI",
        "(islem_id, siparis_id, kaynak_islem_id, istek_no, islem_turu, tutar, durum, islem_zamani)",
    ),
    (
        "TESLIM_ALMA",
        "(siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis, teslim_zamani, dogrulayan_id)",
    ),
    ("DEGERLENDIRME", "(siparis_id, puan, yorum, olusturulma_zamani)"),
]


def read_env(path: Path) -> dict[str, str]:
    values = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    values.update(
        {key: value for key, value in os.environ.items() if key.startswith(("EOD_", "MYSQL_"))}
    )
    return values


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--data", type=Path, default=ROOT / "out", help="CSV directory (default: out/)"
    )
    parser.add_argument("--reset", action="store_true", help="empty the data tables before loading")
    parser.add_argument("--no-load", action="store_true", help="only run the checks of verify.sql")
    parser.add_argument("--host", default="127.0.0.1", help="database host (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=None, help="default: MYSQL_PERF_PORT or 3308")
    parser.add_argument("--env", type=Path, default=REPOSITORY / ".env", help="the .env file")
    return parser.parse_args(argv)


def connect(args: argparse.Namespace, env: dict[str, str]):
    password = env.get("EOD_OWNER_PASSWORD")
    if not password:
        raise SystemExit("EOD_OWNER_PASSWORD is not set; copy .env.example to .env")
    return mysql.connector.connect(
        host=args.host,
        port=args.port or int(env.get("MYSQL_PERF_PORT", "3308")),
        user="eod_owner",
        password=password,
        database="end_of_day",
        allow_local_infile=True,
        allow_local_infile_in_path=str(args.data.resolve()),
        autocommit=False,
    )


def reset(connection) -> None:
    cursor = connection.cursor()
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table, _ in reversed(TABLES):
        cursor.execute(f"TRUNCATE TABLE {table}")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    connection.commit()
    cursor.close()


def load(connection, data: Path) -> list[tuple[str, int, float]]:
    cursor = connection.cursor()
    timings = []
    for table, columns in TABLES:
        path = (data / f"{table}.csv").resolve()
        if not path.exists():
            raise SystemExit(f"missing file {path}; run generate.py first")
        started = time.perf_counter()
        cursor.execute(
            f"LOAD DATA LOCAL INFILE '{path.as_posix()}' INTO TABLE {table} "
            "CHARACTER SET utf8mb4 FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' "
            f"ESCAPED BY '\\\\' LINES TERMINATED BY '\\n' {columns}"
        )
        rows = cursor.rowcount
        # In LOCAL mode MySQL turns a rejected row into a warning and skips it, so warnings and
        # the row count are checked before the commit, and a load that lost rows is undone.
        cursor.execute("SHOW WARNINGS LIMIT 5")
        warnings = cursor.fetchall()
        expected = sum(1 for _ in path.open(encoding="utf-8"))
        if warnings or rows != expected:
            connection.rollback()
            raise SystemExit(f"{table}: loaded {rows} of {expected} rows; warnings: {warnings}")
        connection.commit()
        elapsed = time.perf_counter() - started
        timings.append((table, rows, elapsed))
        print(f"loaded {table:<15} {rows:>9} rows in {elapsed:7.1f} s", flush=True)
    cursor.close()
    return timings


def statements(sql: str) -> list[tuple[str, str]]:
    """The queries of verify.sql with the name from their "-- check:" / "-- scale:" header."""
    result = []
    name, lines = None, []
    for line in sql.splitlines():
        header = re.match(r"^-- (check|scale|summary): (.+)$", line)
        if header:
            name, lines = header.group(1) + ": " + header.group(2).strip(), []
        elif name and line.strip() and not line.startswith("--"):
            lines.append(line)
            if line.rstrip().endswith(";"):
                result.append((name, "\n".join(lines).rstrip().rstrip(";")))
                name, lines = None, []
    return result


def verify(connection) -> bool:
    cursor = connection.cursor()
    passed = True
    for name, query in statements((ROOT / "verify.sql").read_text(encoding="utf-8")):
        started = time.perf_counter()
        cursor.execute(query)
        rows = cursor.fetchall()
        elapsed = time.perf_counter() - started
        if name.startswith("summary"):
            print(f"\n{name}")
            for row in rows:
                print("  " + "  ".join(str(value) for value in row))
            continue
        status = "OK  " if not rows else ("NOTE" if name.startswith("scale") else "FAIL")
        passed = passed and (not rows or name.startswith("scale"))
        print(f"{status} {len(rows):>6} rows  {elapsed:6.1f} s  {name}")
        for row in rows[:3]:
            print("      " + str(row))
    cursor.close()
    return passed


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    env = read_env(args.env)
    connection = connect(args, env)
    try:
        if not args.no_load:
            if args.reset:
                reset(connection)
            started = time.perf_counter()
            load(connection, args.data)
            print(f"total load time {time.perf_counter() - started:.1f} s\n", flush=True)
        return 0 if verify(connection) else 1
    finally:
        connection.close()


if __name__ == "__main__":
    sys.exit(main())
