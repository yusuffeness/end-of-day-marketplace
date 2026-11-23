# Normalization

This document lists the functional dependencies of every table in `V1__create_schema.sql` and shows that each table
is in Boyce-Codd normal form (BCNF).

## Normal forms

- **1NF.** Every column holds one atomic value: there are no lists, repeating groups or composite values. A user's
  roles are not stored in KULLANICI; each user-role pair is a row of the associative table KULLANICI_ROL. The items
  of an order are rows of SIPARIS_KALEMI, not columns of SIPARIS.
- **2NF.** Only KULLANICI_ROL, SIPARIS_KALEMI and the tables keyed by siparis_id have keys that are not a single
  surrogate column. KULLANICI_ROL has no non-key column. In SIPARIS_KALEMI the non-key columns adet and birim_fiyat
  depend on the whole key (siparis_id, ilan_id): the quantity and the unit price belong to one listing within one
  order, not to the order alone or to the listing alone. No non-key column depends on part of a key.
- **3NF.** No non-key column depends on another non-key column. For example, ISLETME stores sehir and ilce but no
  value derived from them, and SIPARIS does not copy the customer's name or the business's address.
- **BCNF.** In every table the determinant of every non-trivial functional dependency is a superkey, as listed below.

## Functional dependencies

Keys are written with their columns; "→ rest" means every other column of the table.

| Table | Candidate keys | Functional dependencies | BCNF justification |
| --- | --- | --- | --- |
| KULLANICI | kullanici_id; eposta | kullanici_id → rest; eposta → rest | Both determinants are candidate keys (eposta is unique). |
| ROL | rol_id; rol_adi | rol_id → rol_adi; rol_adi → rol_id | Both determinants are candidate keys. |
| KULLANICI_ROL | (kullanici_id, rol_id) | none besides the trivial ones | All-key table. |
| ISLETME | isletme_id | isletme_id → rest | The only determinant is the key. Names are not unique (two branches may share a brand), and sehir and ilce determine nothing. |
| PAKET_ILANI | ilan_id | ilan_id → rest | The only determinant is the key. kalan_adet is discussed below. |
| SIPARIS | siparis_id | siparis_id → rest | The only determinant is the key. isletme_id is discussed below. |
| SIPARIS_KALEMI | (siparis_id, ilan_id) | (siparis_id, ilan_id) → adet, birim_fiyat | The only determinant is the key. birim_fiyat is discussed below. |
| ODEME_ISLEMI | islem_id; istek_no | islem_id → rest; istek_no → rest | Both determinants are candidate keys (istek_no is unique). |
| TESLIM_ALMA | siparis_id; teslim_kodu | siparis_id → rest; teslim_kodu → rest | Both determinants are candidate keys (teslim_kodu is unique). The pickup window is discussed below. |
| DEGERLENDIRME | siparis_id | siparis_id → rest | The only determinant is the key. |

## Intentional redundancy

Four columns can look redundant. None of them breaks a normal form, because each one is determined by its own table's
key and by nothing else in that table. Three of them record a value as it was at a point in time, which is a different
fact from the current value elsewhere.

| Column | Why it may look redundant | Why it stays | Why it does not break normalization |
| --- | --- | --- | --- |
| SIPARIS.isletme_id | It can be derived through SIPARIS_KALEMI → PAKET_ILANI → ISLETME. | An order is placed with one business and is queried by business constantly. It also exists before items are added, and the path through the items would have to agree for every item anyway. Trigger EOD-001 keeps the two paths consistent: every item's listing belongs to the order's business, and the business of an order with items cannot change. | The dependency siparis_id → isletme_id holds within SIPARIS and its determinant is the key. The derivation path runs through other tables, so it is not a transitive dependency inside SIPARIS. |
| SIPARIS_KALEMI.birim_fiyat | It equals PAKET_ILANI.satis_fiyati when the order is placed. | It records the price at the moment of purchase (report 4.2). The listing's price may change afterwards; the order must keep what the customer paid. | It depends on the full key (siparis_id, ilan_id). satis_fiyati is the current price of a listing, while birim_fiyat is the agreed price of one order line: two different facts. |
| TESLIM_ALMA.teslim_baslangic, teslim_bitis | They equal the listing's pickup window when the order is placed. | They record the window promised to this order (report 4.4). The pickup check and the not-picked-up event use them even if the listing is edited later. | They depend on siparis_id, the key of TESLIM_ALMA. The listing's window is a property of the listing, the pickup window a property of the order's pickup. |
| PAKET_ILANI.kalan_adet | It can be computed as toplam_adet minus the quantities of orders that were not cancelled. | It is the operational balance that every order decrements and every cancellation restores (report 5.3). Reading one column under a row lock is what keeps concurrent orders from overselling; summing all items on every order would not. | It depends on ilan_id only. It is a stored balance of the listing, not a function of other columns of PAKET_ILANI. The demo data test checks that it matches the items. |
