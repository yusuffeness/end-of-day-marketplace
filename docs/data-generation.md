# Data generation

The performance work of the report (section 8.1) needs a large data set that keeps every rule of the
schema. `datagen/generate.py` writes it as CSV files and `datagen/load.py` loads them into a separate
performance database and runs the consistency checks of `datagen/verify.sql`.

## Running it

The performance data lives in its own MySQL server, so it never mixes with the demo data of the
`dev` profile (whose seed empties every table when it runs). The server is the `mysql-perf` service of
`compose.yaml`, started only with the `perf` compose profile and published on `MYSQL_PERF_PORT`
(default 3308).

```bash
# 1. Start the performance database (same image, settings and accounts as the dev one).
docker compose --profile perf up -d mysql-perf

# 2. Create the schema: start the backend once with the perf profile against that server, wait for
#    "Started EndOfDayBackendApplication", then stop it. Flyway applies the migrations; the perf
#    profile loads no demo data.
cd backend
MYSQL_PORT=3308 ./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
#    PowerShell: $env:MYSQL_PORT = "3308"; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=perf"
cd ..

# 3. Generate and load (Python 3.12 or later).
cd datagen
python -m venv .venv
.venv/bin/pip install -e ".[dev]"        # Windows: .venv\Scripts\pip install -e ".[dev]"
.venv/bin/python generate.py              # writes out/*.csv
.venv/bin/python load.py --reset          # empties the data tables, loads, runs verify.sql
```

`load.py` connects as `eod_owner`, the owner of the schema, with the password from the repository
`.env`; no password is written in the code. `--reset` truncates the nine data tables (ROL and the
Flyway history stay), so the load can be repeated at any time. `load.py --no-load` only runs the
checks. The CSV files (`datagen/out/`) are ignored by Git.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `--seed` | 372 | random seed; the same seed and the same `--today` give byte-identical files |
| `--orders` | 250000 | number of orders |
| `--users` | 20000 (scaled down below 250,000 orders) | number of accounts |
| `--businesses` | 1500 (scaled down below 250,000 orders) | number of businesses |
| `--months` | 12 | months of history before `--today` |
| `--today` | current date | reference day; history ends the day before, confirmed orders lie in the next two days |
| `--out` | `out/` | output directory |

A small trial run: `generate.py --orders 1000 --out out/small` and `load.py --reset --data out/small`
(200 accounts, 30 businesses).

Dates are relative to `--today` on purpose: confirmed orders (ONAYLANDI) must have a pickup window in
the future, otherwise the `evt_mark_not_picked_up` event would change them. Generating on another
day gives the same row counts with shifted dates; passing the same `--today` gives the same data.

## Rules kept by the data

The loader leaves every trigger active, and `LOAD DATA LOCAL` would skip a row that breaks a CHECK
constraint, so `load.py` compares the loaded row count with the file and stops on any warning.

- Accounts: every account is MUSTERI; owners of approved businesses also hold ISLETME_YETKILISI;
  accounts 1 to 5 hold YONETICI. Names come from Faker (`tr_TR`), email addresses use the reserved
  domain `example.com`, phone numbers are in the unused `000` range, and every account shares one
  precomputed BCrypt hash (cost 12). No real personal data is used.
- Listings are opened only for approved and active businesses, with `0 < satis_fiyati <
  normal_fiyat`, a pickup window inside one day (ending at 23:30 at the latest) and
  `kalan_adet = toplam_adet −` the units of orders that were not cancelled.
- An order has one to three listings of its business with overlapping windows; TESLIM_ALMA stores the
  intersection of their windows; the order is created after the listings and before that window
  starts; `birim_fiyat` is the sale price of the listing.
- Every order has exactly one successful TAHSILAT equal to its item total, with a unique `istek_no`.
- TESLIM_EDILDI: picked up inside the window and verified by the owner of the business.
  IPTAL_EDILDI: cancelled before the window starts and refunded in full against the charge (5 % of
  them first have a declined refund attempt, stored as BASARISIZ, as the application does); their
  units are back in stock. TESLIM_ALINMADI: no pickup time, no refund, units not returned.
- ONAYLANDI only for windows in the next two days; no confirmed order has a window that has ended.
- Pickup codes are unique six-digit strings. Reviews (puan 1–5, at most one) only for delivered
  orders, written after the pickup.
- Times are local times of the +03:00 server zone, in order: registration, listing, order, then
  cancellation or pickup, then review.

## Row counts (seed 372, 250,000 orders, `--today` 2026-11-26)

| Table | Rows |
| --- | ---: |
| KULLANICI | 20,000 |
| KULLANICI_ROL | 21,204 |
| ISLETME | 1,500 |
| PAKET_ILANI | 203,353 |
| SIPARIS | 250,000 |
| SIPARIS_KALEMI | 273,474 |
| ODEME_ISLEMI | 270,828 |
| TESLIM_ALMA | 250,000 |
| DEGERLENDIRME | 79,771 |

The 1,000-order trial run gives 200 accounts, 230 role rows, 30 businesses, 1,234 listings, 1,000
orders, 1,079 items, 1,088 payments, 1,000 pickups and 304 reviews.

## Distributions

| Aspect | Result |
| --- | --- |
| Order status | TESLIM_EDILDI 79.85 %, TESLIM_ALINMADI 11.89 %, IPTAL_EDILDI 7.96 %, ONAYLANDI 0.30 % (750, all in the next two days) |
| Businesses | 1,376 approved and active, 42 approved but inactive, 51 BEKLEMEDE, 31 REDDEDILDI; 619 FIRIN, 511 KAFE, 370 RESTORAN |
| Accounts | 20,000 customers, 1,199 business owners (some own two businesses), 5 administrators; 206 inactive accounts; 8,093 without a phone number |
| Long tail | the 10 % busiest businesses (138) take 46.2 % of the orders; orders per business range from 1 to 4,620 (mean 182); 753 businesses have fewer than 100 |
| Cities | İstanbul 53.5 %, Ankara 16.9 %, İzmir 11.6 %, Antalya 4.8 %, Bursa 4.4 %, Konya 2.7 %, Adana 2.3 %, Gaziantep 1.5 %, Kayseri 1.3 %, Eskişehir 0.9 % |
| Time | 2025-11-26 to 2026-11-25 plus the next two days; demand grows over the year (2,406 orders in the first, partial month, 26,110 in October 2026); Saturday and Sunday about a third busier than weekdays |
| Items per order | 1 item 90.8 %, 2 items 9.0 %, 3 items 0.2 % |
| Listings | 162,815 AKTIF, 40,538 PASIF (closed early by the owner); 707 listings can be bought now (AKTIF, stock left, window not started); average sale price 75.47 TL, 43.3 % of the normal price |
| Payments | 250,000 successful charges, 19,894 successful refunds, 934 declined refunds |
| Reviews | 40.0 % of delivered orders; puan 5: 45.0 %, 4: 29.9 %, 3: 13.1 %, 2: 7.0 %, 1: 5.0 %; 40,038 with a comment |

The city shares are more uneven than the city weights of the generator, because a large city has both
more businesses and more orders per business. Orders with several items are rarer than planned
(9.2 % instead of 20 %): an order can only combine packages of the same business and day, and most
business days offer one package.

## Times

Measured on an Intel Core Ultra 9 185H (16 cores, 22 threads), 32 GB RAM, SSD, Windows 11 with Docker
Engine 29.9 (Docker Desktop, WSL 2), MySQL 8.4.13 with a 1 GB buffer pool, Python 3.14.

| Step | Time |
| --- | ---: |
| Generation (all files) | 52.9 s |
| Load KULLANICI | 0.5 s |
| Load KULLANICI_ROL | 0.3 s |
| Load ISLETME | 0.1 s |
| Load PAKET_ILANI | 6.4 s |
| Load SIPARIS | 4.4 s |
| Load SIPARIS_KALEMI | 7.3 s |
| Load ODEME_ISLEMI | 19.8 s |
| Load TESLIM_ALMA | 10.4 s |
| Load DEGERLENDIRME | 1.5 s |
| Load total | 50.7 s |

ODEME_ISLEMI and TESLIM_ALMA take longest because their insert triggers read the order's other
payments and the owner of the business for every row.

## verify.sql results

Every check returns zero rows on the full data set and on the 1,000-order trial (where only the scale
check reports the smaller size, as intended).

| Check | Rows |
| --- | ---: |
| listings whose remaining stock is not the total minus the units of orders that were not cancelled | 0 |
| orders with more than one successful charge | 0 |
| orders whose successful charge is missing or differs from the item total | 0 |
| orders without items | 0 |
| orders whose successful refunds exceed the charge | 0 |
| cancelled orders without a full successful refund of their charge | 0 |
| successful refunds of orders that were not cancelled | 0 |
| orders with items of a listing of another business | 0 |
| items whose unit price is not the sale price of the listing | 0 |
| pickup windows that are not the intersection of the windows of the order's listings | 0 |
| orders created at or after the start of their pickup window | 0 |
| orders without a pickup record | 0 |
| delivered orders picked up outside the window, and pickup times of orders that were not delivered | 0 |
| pickups verified by someone other than the owner of the order's business | 0 |
| cancellations at or after the start of the pickup window | 0 |
| reviews of orders that were not delivered, or written before the pickup | 0 |
| listings of businesses that are not approved and active | 0 |
| listings whose pickup window does not lie within one day | 0 |
| orders placed before the listing was created or before the customer registered | 0 |
| confirmed orders whose pickup window has ended | 0 |
| business owners without the ISLETME_YETKILISI role for an approved business | 0 |
| scale: SIPARIS has at least 250,000 rows | 0 (250,000 rows) |

Generating twice with seed 372 and the same `--today` produced byte-identical files.

## Analytical queries on this data

The five queries of [analytical-queries.md](analytical-queries.md) all return non-empty, plausible
results on the full data set, without the indexes of the performance work:

| Query | Rows | Example of the first rows |
| --- | ---: | --- |
| Q1 monthly net collection above the monthly average | 4,523 | 2025-11: a Karşıyaka restaurant with 7,865.00 TL against an average of 323.27 TL |
| Q2 sales above the district average | 457 | Adana / Çukurova: 323 units against a district average of 71.89 |
| Q3 three best-selling listings per city | 195 | 10 cities; ties share a rank, so a city may list more than three |
| Q4 businesses of repeat customers | 1,363 | the busiest business has 3,531 repeat customers with 4,243 orders |
| Q5 monthly not-picked-up rate above the platform rate | 6,314 | platform rate 0.13; small businesses with one missed pickup in a month show a rate of 1.00 |

Their run times are measured with and without indexes in the performance document.
