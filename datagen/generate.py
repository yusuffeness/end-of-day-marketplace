"""Generate CSV data sets for the end_of_day database.

The files are written to an output directory and loaded with LOAD DATA LOCAL INFILE (load.py).

The data follows every rule of the schema and its triggers (report sections 3 to 5.1):

* listings belong to approved and active businesses, are discounted and have a pickup window
  inside one day;
* every item of an order comes from the order's business, the unit price is the listing's sale
  price, and the order is created before the common pickup window (the intersection of the
  windows of its listings) starts; that window is stored in TESLIM_ALMA;
* the remaining stock of a listing is its total minus the units of orders that were not
  cancelled;
* every order has exactly one successful charge equal to its item total; a cancelled order is
  refunded in full against that charge before its window starts;
* delivered orders are verified inside the window by the owner of the business, orders that
  were not picked up have no pickup time and no refund, reviews follow a delivery.

All dates are relative to --today (default: the current date). History covers the months before
it; a small share of orders lies in the next two days and is still confirmed (ONAYLANDI), so
that listings that can be bought exist. No confirmed order has a window that has ended.

The same seed and the same --today always produce the same files.
"""

import argparse
import bisect
import csv
import random
import sys
import time
import uuid
from datetime import date, datetime, timedelta
from pathlib import Path

from faker import Faker

# One precomputed BCrypt hash (cost 12) for every generated account; hashing each row would only
# make the generator slow. The plain password is not part of the generated data set.
PASSWORD_HASH = "$2a$12$dhTY0Pl3r8RFcuArmaQ0ouLliZQRwzWC9R1SmZ6pFVmk5TGl72OB2"
EMAIL_DOMAIN = "example.com"

# City, weight of the city in the orders, districts.
CITIES = [
    ("İstanbul", 30, ["Kadıköy", "Beşiktaş", "Şişli", "Üsküdar", "Bakırköy", "Ataşehir", "Fatih"]),
    ("Ankara", 15, ["Çankaya", "Keçiören", "Yenimahalle", "Etimesgut", "Mamak", "Altındağ"]),
    ("İzmir", 12, ["Konak", "Karşıyaka", "Bornova", "Buca", "Bayraklı"]),
    ("Bursa", 9, ["Osmangazi", "Nilüfer", "Yıldırım", "Mudanya"]),
    ("Antalya", 8, ["Muratpaşa", "Konyaaltı", "Kepez", "Alanya"]),
    ("Konya", 6, ["Selçuklu", "Meram", "Karatay", "Ereğli"]),
    ("Adana", 6, ["Seyhan", "Çukurova", "Yüreğir", "Sarıçam"]),
    ("Gaziantep", 5, ["Şahinbey", "Şehitkamil", "Nizip", "İslahiye"]),
    ("Kayseri", 5, ["Melikgazi", "Kocasinan", "Talas", "Develi"]),
    ("Eskişehir", 4, ["Odunpazarı", "Tepebaşı", "Sivrihisar", "Alpu"]),
]

# Business type: share, name suffix, first pickup hour, normal price range, package names.
TYPES = {
    "FIRIN": (
        0.40,
        "Fırını",
        17,
        (60, 180),
        [
            "Akşam ekmek paketi",
            "Poğaça ve simit paketi",
            "Günün pastaları",
            "Kurabiye kutusu",
            "Börek paketi",
        ],
    ),
    "KAFE": (
        0.35,
        "Kafe",
        18,
        (90, 260),
        [
            "Tatlı paketi",
            "Sandviç ve kek paketi",
            "Kahvaltılık paketi",
            "Cheesecake dilimleri",
            "Brownie kutusu",
        ],
    ),
    "RESTORAN": (
        0.25,
        "Lokantası",
        20,
        (120, 400),
        [
            "Günün yemekleri",
            "Ev yemekleri paketi",
            "Izgara menü",
            "Vejetaryen tabak",
            "Çorba ve pilav paketi",
        ],
    ),
}

ALLERGENS = [
    "Gluten, süt ve yumurta içerir.",
    "Gluten ve susam içerir.",
    "Süt ürünleri ve fındık içerir.",
    "Gluten, süt, yumurta ve ceviz içerir.",
    "Bilinen bir alerjen içermez.",
]

COMMENTS = {
    5: [
        "Çok taze ve lezzetliydi.",
        "Paket beklediğimden doluydu, teşekkürler.",
        "Her zaman aldığım yer, yine çok iyiydi.",
    ],
    4: ["Güzeldi, biraz daha çeşit olabilirdi.", "Lezzetliydi, teslim hızlıydı."],
    3: ["Fena değildi.", "Ortalama bir paketti."],
    2: ["Ürünler bayattı.", "Teslimde biraz beklettiler."],
    1: ["Beklentimi karşılamadı.", "Paket açıklamaya uymuyordu."],
}

STATUS_WEIGHTS = [("TESLIM_EDILDI", 80), ("TESLIM_ALINMADI", 12), ("IPTAL_EDILDI", 8)]
ITEM_COUNT_WEIGHTS = [(1, 80), (2, 17), (3, 3)]
QUANTITY_WEIGHTS = [(1, 70), (2, 22), (3, 8)]
RATING_WEIGHTS = [(5, 45), (4, 30), (3, 13), (2, 7), (1, 5)]
REVIEW_SHARE = 0.40
COMMENT_SHARE = 0.5
FAILED_REFUND_SHARE = 0.05
INACTIVE_LISTING_SHARE = 0.20
LIVE_ORDER_SHARE = 0.003
LIVE_DAYS = 2

TRANSLITERATION = str.maketrans("çğıöşüÇĞİÖŞÜâîû", "cgiosuCGIOSUaiu")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--seed",
        type=int,
        default=372,
        help="random seed, so that the same seed always produces the same data (default: 372)",
    )
    parser.add_argument(
        "--orders",
        type=int,
        default=250_000,
        help="number of orders to generate (default: 250000)",
    )
    parser.add_argument(
        "--users",
        type=int,
        default=None,
        help="number of accounts (default: 20000, scaled down for fewer than 250000 orders)",
    )
    parser.add_argument(
        "--businesses",
        type=int,
        default=None,
        help="number of businesses (default: 1500, scaled down for fewer than 250000 orders)",
    )
    parser.add_argument(
        "--months",
        type=int,
        default=12,
        help="months of history before --today (default: 12)",
    )
    parser.add_argument(
        "--today",
        type=date.fromisoformat,
        default=None,
        help="reference day as YYYY-MM-DD (default: the current date)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("out"),
        help="directory the CSV files are written to (default: out/)",
    )
    args = parser.parse_args(argv)
    scale = min(1.0, args.orders / 250_000)
    if args.users is None:
        args.users = max(200, round(20_000 * scale))
    if args.businesses is None:
        args.businesses = max(30, round(1_500 * scale))
    if args.today is None:
        args.today = date.today()
    if args.orders < 1 or args.users < 20 or args.businesses < 10 or args.months < 1:
        parser.error("--orders >= 1, --users >= 20, --businesses >= 10 and --months >= 1")
    return args


def pick(rng: random.Random, weighted: list[tuple]) -> object:
    values = [value for value, _ in weighted]
    weights = [weight for _, weight in weighted]
    return rng.choices(values, weights)[0]


def stamp(moment: datetime) -> str:
    return moment.strftime("%Y-%m-%d %H:%M:%S")


def money(kurus: int) -> str:
    return f"{kurus // 100}.{kurus % 100:02d}"


def slug(text: str) -> str:
    return "".join(ch for ch in text.translate(TRANSLITERATION).lower() if ch.isalnum())


def fictional_phone(number: int) -> str:
    """A phone number in the reserved-looking 000 range, so that it never belongs to anyone."""
    digits = f"{number:07d}"
    return f"000 {digits[:3]} {digits[3:5]} {digits[5:]}"


class Generator:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.rng = random.Random(args.seed)
        self.faker = Faker("tr_TR")
        self.faker.seed_instance(args.seed)
        self.today = datetime.combine(args.today, datetime.min.time())
        self.history_days = round(args.months * 365 / 12)
        self.first_day = self.today - timedelta(days=self.history_days)
        self.request_ids: set[str] = set()

    # Accounts and businesses

    def users(self) -> None:
        rng = self.rng
        self.user_rows = []
        moments = []
        for user_id in range(1, self.args.users + 1):
            first = self.faker.first_name()
            last = self.faker.last_name()
            # A third registered before the history starts, the others during it.
            if user_id <= 5 or rng.random() < 0.35:
                registered = self.first_day - timedelta(minutes=rng.randrange(1, 365 * 24 * 60))
            else:
                registered = self.first_day + timedelta(
                    minutes=rng.randrange(0, (self.history_days - 1) * 24 * 60)
                )
            moments.append((registered, user_id))
            phone = fictional_phone(user_id) if rng.random() < 0.6 else None
            email = f"{slug(first)}.{slug(last)}.{user_id}@{EMAIL_DOMAIN}"
            active = 1 if user_id <= 5 or rng.random() > 0.01 else 0
            self.user_rows.append(
                [user_id, first, last, email, phone, PASSWORD_HASH, active, registered]
            )
        for row in self.user_rows:
            row[7] = stamp(row[7])
        moments.sort()
        self.customers_by_time = [user_id for _, user_id in moments]
        self.registration_times = [moment for moment, _ in moments]
        self.admins = list(range(1, 6))

    def businesses(self) -> None:
        rng = self.rng
        early = [
            user_id
            for moment, user_id in zip(self.registration_times, self.customers_by_time, strict=True)
            if moment < self.first_day and user_id > 5
        ]
        owner_count = max(1, round(self.args.businesses / 1.2))
        owners = rng.sample(early, min(owner_count, len(early)))
        type_names = list(TYPES)
        type_weights = [TYPES[name][0] for name in type_names]
        city_weights = [weight for _, weight, _ in CITIES]
        self.business_rows = []
        self.businesses_info = []
        for business_id in range(1, self.args.businesses + 1):
            owner = owners[business_id - 1] if business_id <= len(owners) else rng.choice(owners)
            kind = rng.choices(type_names, type_weights)[0]
            city_index = rng.choices(range(len(CITIES)), city_weights)[0]
            city, _, districts = CITIES[city_index]
            district = rng.choice(districts)
            roll = rng.random()
            if roll < 0.92:
                approval, active = "ONAYLANDI", 1
            elif roll < 0.95:
                approval, active = "ONAYLANDI", 0
            elif roll < 0.98:
                approval, active = "BEKLEMEDE", 1
            else:
                approval, active = "REDDEDILDI", 1
            name = f"{self.faker.last_name()} {TYPES[kind][1]} {district}"
            address = f"{self.faker.street_name()} No: {rng.randint(1, 180)}"
            self.business_rows.append(
                [
                    business_id,
                    owner,
                    name[:100],
                    kind,
                    city,
                    district,
                    address,
                    fictional_phone(5_000_000 + business_id),
                    approval,
                    active,
                ]
            )
            # Popularity has a long tail: a few businesses sell a lot, most sell little.
            popularity = rng.lognormvariate(0, 1.1) * CITIES[city_index][1]
            self.businesses_info.append(
                {
                    "id": business_id,
                    "owner": owner,
                    "kind": kind,
                    "open": approval == "ONAYLANDI" and active == 1,
                    "popularity": popularity,
                }
            )
        self.owner_ids = sorted({row[1] for row in self.business_rows})

    def roles(self) -> list[list]:
        rows = [[user_id, "MUSTERI"] for user_id in range(1, self.args.users + 1)]
        owners_with_role = {row[1] for row in self.business_rows if row[8] == "ONAYLANDI"}
        rows += [[user_id, "ISLETME_YETKILISI"] for user_id in sorted(owners_with_role)]
        rows += [[user_id, "YONETICI"] for user_id in self.admins]
        return rows

    # Orders and everything that hangs on them

    def day_weights(self, days: list[datetime]) -> list[float]:
        weights = []
        for index, day in enumerate(days):
            weekday = {4: 1.15, 5: 1.35, 6: 1.30}.get(day.weekday(), 1.0)
            trend = 0.7 + 0.6 * index / max(1, len(days) - 1)
            weights.append(weekday * trend)
        return weights

    def plan_orders(self) -> dict[tuple[int, int], list[int]]:
        """Assigns every order to a business and a day; returns the orders per (business, day)."""
        rng = self.rng
        open_businesses = [info for info in self.businesses_info if info["open"]]
        business_weights = [info["popularity"] for info in open_businesses]
        days = [self.first_day + timedelta(days=offset) for offset in range(self.history_days)]
        live_days = [self.today + timedelta(days=offset) for offset in range(1, LIVE_DAYS + 1)]
        live_orders = min(self.args.orders - 1, round(self.args.orders * LIVE_ORDER_SHARE))
        history_orders = self.args.orders - live_orders
        day_list = days + live_days
        chosen_days = rng.choices(range(len(days)), self.day_weights(days), k=history_orders)
        chosen_days += [len(days) + rng.randrange(LIVE_DAYS) for _ in range(live_orders)]
        chosen = rng.choices(range(len(open_businesses)), business_weights, k=self.args.orders)
        groups: dict[tuple[int, int], list[int]] = {}
        for number, (business_index, day_index) in enumerate(zip(chosen, chosen_days, strict=True)):
            groups.setdefault((business_index, day_index), []).append(number)
        self.day_list = day_list
        self.open_businesses = open_businesses
        self.history_day_count = len(days)
        return groups

    def listings_for(self, info: dict, day: datetime, count: int, live: bool) -> list[dict]:
        rng = self.rng
        _, _, first_hour, (low, high), names = TYPES[info["kind"]]
        base = day + timedelta(hours=first_hour, minutes=rng.choice([0, 30, 60, 90]))
        created_base = day + timedelta(hours=rng.randint(8, 11), minutes=rng.randrange(60))
        if live:
            created_base = self.today - timedelta(days=1) + timedelta(hours=rng.randint(9, 11))
        listings = []
        for index in range(count):
            start = base + timedelta(minutes=rng.choice([0, 15, 30]))
            # The window ends on the same day: at 23:30 at the latest.
            end = min(
                start + timedelta(minutes=rng.choice([60, 90, 120])),
                day + timedelta(hours=23, minutes=30),
            )
            normal = rng.randrange(low, high + 1, 5) * 100
            sale = max(500, (normal * rng.randint(30, 60) // 100) // 500 * 500)
            listings.append(
                {
                    "business": info["id"],
                    "title": names[(index + day.day) % len(names)],
                    "allergen": rng.choice(ALLERGENS),
                    "normal": normal,
                    "sale": sale,
                    "start": start,
                    "end": end,
                    "created": created_base + timedelta(minutes=index * rng.randint(1, 20)),
                    "sold": 0,
                    "kept": 0,
                    "live": live,
                }
            )
        return listings

    def customer_before(self, moment: datetime) -> int:
        """A customer registered before the moment; earlier accounts order more often."""
        bound = bisect.bisect_left(self.registration_times, moment)
        index = int(bound * (self.rng.random() ** 1.6))
        return self.customers_by_time[min(index, bound - 1)]

    def request_id(self) -> str:
        while True:
            value = str(uuid.UUID(int=self.rng.getrandbits(128), version=4))
            if value not in self.request_ids:
                self.request_ids.add(value)
                return value

    def orders(self) -> None:
        rng = self.rng
        groups = self.plan_orders()
        listings: list[dict] = []
        orders: list[dict] = []
        for (business_index, day_index), numbers in sorted(groups.items()):
            info = self.open_businesses[business_index]
            day = self.day_list[day_index]
            live = day_index >= self.history_day_count
            # One to four packages a day; busy days offer more.
            count = 1 + (rng.random() < 0.35) + (len(numbers) > 4) + (len(numbers) > 12)
            day_listings = self.listings_for(info, day, count, live)
            listings.extend(day_listings)
            for _ in numbers:
                item_count = min(pick(rng, ITEM_COUNT_WEIGHTS), len(day_listings))
                chosen = rng.sample(day_listings, item_count)
                window_start = max(listing["start"] for listing in chosen)
                window_end = min(listing["end"] for listing in chosen)
                opened = max(listing["created"] for listing in chosen)
                latest = window_start - timedelta(minutes=10)
                if live:
                    latest = min(latest, self.today - timedelta(minutes=30))
                span = max(1, int((latest - opened).total_seconds() // 60) - 5)
                created = opened + timedelta(minutes=5 + rng.randrange(span))
                status = "ONAYLANDI" if live else pick(rng, STATUS_WEIGHTS)
                items = [(listing, pick(rng, QUANTITY_WEIGHTS)) for listing in chosen]
                for listing, quantity in items:
                    listing["sold"] += quantity
                    if status != "IPTAL_EDILDI":
                        listing["kept"] += quantity
                orders.append(
                    {
                        "business": info,
                        "created": created,
                        "status": status,
                        "items": items,
                        "start": window_start,
                        "end": window_end,
                        "customer": self.customer_before(created),
                    }
                )
        # Identifiers follow the creation times, as AUTO_INCREMENT would assign them.
        listings.sort(key=lambda listing: (listing["created"], listing["business"]))
        for listing_id, listing in enumerate(listings, start=1):
            listing["id"] = listing_id
        orders.sort(key=lambda order: (order["created"], order["business"]["id"]))
        self.listings = listings
        self.order_list = orders

    def rows(self) -> dict[str, list[list]]:
        rng = self.rng
        listing_rows = []
        for listing in self.listings:
            # A package that sold nothing still had at least one unit (toplam_adet > 0).
            total = listing["sold"] + rng.randint(0 if listing["sold"] else 1, 4)
            if listing["live"]:
                total += rng.randint(2, 6)
            status = (
                "PASIF"
                if not listing["live"] and rng.random() < INACTIVE_LISTING_SHARE
                else "AKTIF"
            )
            listing_rows.append(
                [
                    listing["id"],
                    listing["business"],
                    listing["title"],
                    f"{listing['title']}: günün ürünlerinden karışık paket.",
                    listing["allergen"],
                    money(listing["normal"]),
                    money(listing["sale"]),
                    total,
                    total - listing["kept"],
                    stamp(listing["start"]),
                    stamp(listing["end"]),
                    status,
                    stamp(listing["created"]),
                ]
            )
        codes = rng.sample(range(1_000_000), len(self.order_list))
        order_rows, item_rows, payments, pickup_rows, review_rows = [], [], [], [], []
        for order_id, (order, code) in enumerate(zip(self.order_list, codes, strict=True), start=1):
            created, start, end = order["created"], order["start"], order["end"]
            cancelled = None
            total = 0
            for listing, quantity in sorted(order["items"], key=lambda item: item[0]["id"]):
                item_rows.append([order_id, listing["id"], quantity, money(listing["sale"])])
                total += quantity * listing["sale"]
            payments.append(
                (
                    created,
                    0,
                    [
                        order_id,
                        None,
                        self.request_id(),
                        "TAHSILAT",
                        money(total),
                        "BASARILI",
                        stamp(created),
                    ],
                )
            )
            picked_up = verifier = None
            if order["status"] == "IPTAL_EDILDI":
                room = max(1, int((start - created).total_seconds() // 60) - 2)
                cancelled = created + timedelta(minutes=1 + rng.randrange(room))
                if rng.random() < FAILED_REFUND_SHARE:
                    payments.append(
                        (
                            cancelled,
                            1,
                            [
                                order_id,
                                "charge",
                                self.request_id(),
                                "IADE",
                                money(total),
                                "BASARISIZ",
                                stamp(cancelled),
                            ],
                        )
                    )
                payments.append(
                    (
                        cancelled,
                        2,
                        [
                            order_id,
                            "charge",
                            self.request_id(),
                            "IADE",
                            money(total),
                            "BASARILI",
                            stamp(cancelled),
                        ],
                    )
                )
            elif order["status"] == "TESLIM_EDILDI":
                seconds = int((end - start).total_seconds())
                picked_up = start + timedelta(seconds=rng.randrange(seconds + 1))
                verifier = order["business"]["owner"]
                if rng.random() < REVIEW_SHARE:
                    latest = min(picked_up + timedelta(days=3), self.today - timedelta(minutes=1))
                    room = max(1, int((latest - picked_up).total_seconds() // 60))
                    reviewed = picked_up + timedelta(minutes=1 + rng.randrange(room))
                    rating = pick(rng, RATING_WEIGHTS)
                    comment = rng.choice(COMMENTS[rating]) if rng.random() < COMMENT_SHARE else None
                    review_rows.append([order_id, rating, comment, stamp(reviewed)])
            order_rows.append(
                [
                    order_id,
                    order["customer"],
                    order["business"]["id"],
                    stamp(created),
                    order["status"],
                    stamp(cancelled) if cancelled else None,
                ]
            )
            pickup_rows.append(
                [
                    order_id,
                    f"{code:06d}",
                    stamp(start),
                    stamp(end),
                    stamp(picked_up) if picked_up else None,
                    verifier,
                ]
            )
        # A refund refers to its charge, so charges get their ids first in time order.
        payments.sort(key=lambda payment: (payment[0], payment[1], payment[2][0]))
        payment_rows = []
        charge_of: dict[int, int] = {}
        for payment_id, (_, _, row) in enumerate(payments, start=1):
            if row[3] == "TAHSILAT":
                charge_of[row[0]] = payment_id
            else:
                row[1] = charge_of[row[0]]
            payment_rows.append([payment_id, *row])
        return {
            "KULLANICI": self.user_rows,
            "KULLANICI_ROL": self.roles(),
            "ISLETME": self.business_rows,
            "PAKET_ILANI": listing_rows,
            "SIPARIS": order_rows,
            "SIPARIS_KALEMI": item_rows,
            "ODEME_ISLEMI": payment_rows,
            "TESLIM_ALMA": pickup_rows,
            "DEGERLENDIRME": review_rows,
        }


def write_csv(path: Path, rows: list[list]) -> None:
    """Comma separated, double quotes around text when needed, \\N for NULL (LOAD DATA format)."""
    with path.open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out, lineterminator="\n")
        for row in rows:
            writer.writerow(["\\N" if value is None else value for value in row])


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    started = time.perf_counter()
    generator = Generator(args)
    generator.users()
    generator.businesses()
    generator.orders()
    tables = generator.rows()
    args.out.mkdir(parents=True, exist_ok=True)
    for table, rows in tables.items():
        write_csv(args.out / f"{table}.csv", rows)
    elapsed = time.perf_counter() - started
    print(
        f"seed={args.seed} today={args.today} orders={args.orders} users={args.users} "
        f"businesses={args.businesses} months={args.months}"
    )
    for table, rows in tables.items():
        print(f"{table:<15} {len(rows):>9}")
    print(f"generated in {elapsed:.1f} s -> {args.out.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
