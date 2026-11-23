# Conceptual ER diagram

The conceptual model of the platform. Rendered image: [er-diagram.png](er-diagram.png).

```mermaid
erDiagram
    KULLANICI }o--|{ ROL : "holds"
    KULLANICI ||--o{ ISLETME : "owns (yetkili)"
    ISLETME ||--o{ PAKET_ILANI : "publishes"
    KULLANICI ||--o{ SIPARIS : "places (musteri)"
    ISLETME ||--o{ SIPARIS : "receives"
    SIPARIS ||--|{ SIPARIS_KALEMI : "contains"
    PAKET_ILANI ||--o{ SIPARIS_KALEMI : "is ordered as"
    SIPARIS ||--|{ ODEME_ISLEMI : "is paid by"
    ODEME_ISLEMI |o--o{ ODEME_ISLEMI : "charge is refunded by"
    SIPARIS ||--o| TESLIM_ALMA : "is picked up through"
    KULLANICI |o--o{ TESLIM_ALMA : "verifies (dogrulayan)"
    SIPARIS ||--o| DEGERLENDIRME : "is reviewed by"

    KULLANICI {
        id kullanici_id PK
        string eposta UK
        string ad
        string soyad
    }
    ROL {
        id rol_id PK
        string rol_adi UK
    }
    ISLETME {
        id isletme_id PK
        string isletme_adi
        string isletme_turu
        string onay_durumu
    }
    PAKET_ILANI {
        id ilan_id PK
        string baslik
        money satis_fiyati
        int kalan_adet
    }
    SIPARIS {
        id siparis_id PK
        string durum
    }
    SIPARIS_KALEMI {
        int adet
        money birim_fiyat
    }
    ODEME_ISLEMI {
        id islem_id PK
        string istek_no UK
        string islem_turu
        money tutar
    }
    TESLIM_ALMA {
        string teslim_kodu UK
        datetime teslim_baslangic
        datetime teslim_bitis
    }
    DEGERLENDIRME {
        int puan
        string yorum
    }
```

## Relationships

| Relationship | Cardinality and participation |
| --- | --- |
| KULLANICI holds ROL | M:N through KULLANICI_ROL. Every user holds at least one role (MUSTERI on registration); a role is held by any number of users. |
| KULLANICI owns ISLETME | 1:N. A business has exactly one owner (yetkili_id); a user owns zero or more businesses, one per branch. |
| ISLETME publishes PAKET_ILANI | 1:N. A listing belongs to exactly one business; a business has zero or more listings. |
| KULLANICI places SIPARIS | 1:N. An order has exactly one customer (musteri_id); a user places zero or more orders. |
| ISLETME receives SIPARIS | 1:N. An order is placed with exactly one business; a business receives zero or more orders. |
| SIPARIS contains PAKET_ILANI | M:N through the associative entity SIPARIS_KALEMI (adet, birim_fiyat). An order contains at least one listing, all of its own business; a listing appears in zero or more orders. |
| SIPARIS is paid by ODEME_ISLEMI | 1:N. A transaction belongs to exactly one order; an order has at least one transaction, of which exactly one is a successful charge. |
| ODEME_ISLEMI (charge) is refunded by ODEME_ISLEMI (refund) | Recursive 1:N through kaynak_islem_id. A refund refers to exactly one charge; a charge has zero or more refunds; a charge refers to none. |
| SIPARIS is picked up through TESLIM_ALMA | 1:0..1. A pickup record belongs to exactly one order (siparis_id is its primary key); an order has at most one. |
| KULLANICI verifies TESLIM_ALMA | 0..1:N. A pickup is verified by at most one user, the owner of the order's business; a user verifies zero or more pickups. |
| SIPARIS is reviewed by DEGERLENDIRME | 1:0..1. A review belongs to exactly one delivered order (siparis_id is its primary key); an order has at most one. |

Only identifying and characteristic attributes are shown; the full column list is in
[relational-schema.md](relational-schema.md).
