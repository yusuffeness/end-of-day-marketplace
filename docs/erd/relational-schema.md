# Relational schema

All tables of `V1__create_schema.sql` with their column types and keys. Rendered image:
[relational-schema.png](relational-schema.png).

Markers: PK primary key, FK foreign key, UK unique key. Money columns are `DECIMAL(10,2)`; Mermaid does not allow the
comma in a type, so the precision is given as a comment. Boolean columns are `BOOLEAN` (`TINYINT(1)` in MySQL).

```mermaid
erDiagram
    KULLANICI ||--o{ KULLANICI_ROL : "kullanici_id"
    ROL ||--o{ KULLANICI_ROL : "rol_id"
    KULLANICI ||--o{ ISLETME : "yetkili_id"
    ISLETME ||--o{ PAKET_ILANI : "isletme_id"
    KULLANICI ||--o{ SIPARIS : "musteri_id"
    ISLETME ||--o{ SIPARIS : "isletme_id"
    SIPARIS ||--o{ SIPARIS_KALEMI : "siparis_id"
    PAKET_ILANI ||--o{ SIPARIS_KALEMI : "ilan_id"
    SIPARIS ||--o{ ODEME_ISLEMI : "siparis_id"
    ODEME_ISLEMI |o--o{ ODEME_ISLEMI : "kaynak_islem_id"
    SIPARIS ||--o| TESLIM_ALMA : "siparis_id"
    KULLANICI |o--o{ TESLIM_ALMA : "dogrulayan_id"
    SIPARIS ||--o| DEGERLENDIRME : "siparis_id"

    KULLANICI {
        BIGINT_UNSIGNED kullanici_id PK
        VARCHAR(50) ad
        VARCHAR(50) soyad
        VARCHAR(254) eposta UK "utf8mb4_0900_as_ci"
        VARCHAR(20) telefon "NULL"
        VARCHAR(100) parola_ozeti
        BOOLEAN aktif_mi
        DATETIME kayit_zamani
    }
    ROL {
        BIGINT_UNSIGNED rol_id PK
        VARCHAR(30) rol_adi UK
    }
    KULLANICI_ROL {
        BIGINT_UNSIGNED kullanici_id PK, FK "ON DELETE CASCADE"
        BIGINT_UNSIGNED rol_id PK, FK
    }
    ISLETME {
        BIGINT_UNSIGNED isletme_id PK
        BIGINT_UNSIGNED yetkili_id FK
        VARCHAR(100) isletme_adi
        VARCHAR(20) isletme_turu "FIRIN, KAFE, RESTORAN"
        VARCHAR(50) sehir
        VARCHAR(50) ilce
        VARCHAR(255) adres
        VARCHAR(20) telefon
        VARCHAR(20) onay_durumu "BEKLEMEDE, ONAYLANDI, REDDEDILDI"
        BOOLEAN aktif_mi
    }
    PAKET_ILANI {
        BIGINT_UNSIGNED ilan_id PK
        BIGINT_UNSIGNED isletme_id FK
        VARCHAR(100) baslik
        TEXT aciklama
        VARCHAR(500) alerjen_bilgisi
        DECIMAL normal_fiyat "(10,2)"
        DECIMAL satis_fiyati "(10,2)"
        INT_UNSIGNED toplam_adet
        INT_UNSIGNED kalan_adet
        DATETIME teslim_baslangic
        DATETIME teslim_bitis
        VARCHAR(10) durum "AKTIF, PASIF"
        DATETIME olusturulma_zamani
    }
    SIPARIS {
        BIGINT_UNSIGNED siparis_id PK
        BIGINT_UNSIGNED musteri_id FK
        BIGINT_UNSIGNED isletme_id FK
        DATETIME olusturulma_zamani
        VARCHAR(20) durum "ONAYLANDI, TESLIM_EDILDI, IPTAL_EDILDI, TESLIM_ALINMADI"
        DATETIME iptal_zamani "NULL"
    }
    SIPARIS_KALEMI {
        BIGINT_UNSIGNED siparis_id PK, FK
        BIGINT_UNSIGNED ilan_id PK, FK
        INT_UNSIGNED adet
        DECIMAL birim_fiyat "(10,2)"
    }
    ODEME_ISLEMI {
        BIGINT_UNSIGNED islem_id PK
        BIGINT_UNSIGNED siparis_id FK
        BIGINT_UNSIGNED kaynak_islem_id FK "NULL"
        VARCHAR(64) istek_no UK
        VARCHAR(10) islem_turu "TAHSILAT, IADE"
        DECIMAL tutar "(10,2)"
        VARCHAR(10) durum "BASARILI, BASARISIZ"
        DATETIME islem_zamani
    }
    TESLIM_ALMA {
        BIGINT_UNSIGNED siparis_id PK, FK
        CHAR(6) teslim_kodu UK
        DATETIME teslim_baslangic
        DATETIME teslim_bitis
        DATETIME teslim_zamani "NULL"
        BIGINT_UNSIGNED dogrulayan_id FK "NULL"
    }
    DEGERLENDIRME {
        BIGINT_UNSIGNED siparis_id PK, FK
        TINYINT_UNSIGNED puan "1 to 5"
        VARCHAR(1000) yorum "NULL"
        DATETIME olusturulma_zamani
    }
```

Unless marked NULL, every column is NOT NULL. All foreign keys use ON DELETE RESTRICT ON UPDATE RESTRICT, except
`KULLANICI_ROL.kullanici_id`, which cascades deletes. The check constraints are listed in
[../constraint-tests.md](../constraint-tests.md).
