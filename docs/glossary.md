# Glossary

Database identifiers are Turkish as defined in the project report; code uses the English names below.

## Tables

| Database table | Code name | Meaning |
| --- | --- | --- |
| `KULLANICI` | `User` | A registered account of the platform. |
| `ROL` | `Role` | A role that grants a set of permissions, such as customer, business owner or administrator. |
| `KULLANICI_ROL` | `UserRole` | The assignment of a role to a user. |
| `ISLETME` | `Business` | A bakery, café or restaurant that sells surplus food on the platform. |
| `PAKET_ILANI` | `Listing` | A discounted surplus food package offered by a business with a pickup window. |
| `SIPARIS` | `Order` | A customer's order placed with a business. |
| `SIPARIS_KALEMI` | `OrderItem` | A single listing and its quantity within an order. |
| `ODEME_ISLEMI` | `PaymentTransaction` | A payment or refund transaction recorded for an order. |
| `TESLIM_ALMA` | `Pickup` | The record of a customer collecting an order at the business. |
| `DEGERLENDIRME` | `Review` | A customer's rating and comment about a completed order. |

## Status values

| Column | Value | Meaning |
| --- | --- | --- |
| `SIPARIS.durum` | `ONAYLANDI` | Confirmed: the order is placed and awaiting pickup. |
| `SIPARIS.durum` | `TESLIM_EDILDI` | Delivered: the customer collected the order. |
| `SIPARIS.durum` | `IPTAL_EDILDI` | Cancelled: the order was cancelled. |
| `SIPARIS.durum` | `TESLIM_ALINMADI` | Not picked up: the pickup window ended without the order being collected. |
| `PAKET_ILANI.durum` | `AKTIF` | Active: the listing is visible and can be ordered. |
| `PAKET_ILANI.durum` | `PASIF` | Inactive: the listing is hidden and cannot be ordered. |
| `ODEME_ISLEMI.islem_turu` | `TAHSILAT` | Charge: money collected from the customer. |
| `ODEME_ISLEMI.islem_turu` | `IADE` | Refund: money returned to the customer. |
| `ODEME_ISLEMI.durum` | `BASARILI` | Successful: the transaction completed. |
| `ODEME_ISLEMI.durum` | `BASARISIZ` | Failed: the transaction did not complete. |
| `ISLETME.isletme_turu` | `FIRIN` | Bakery. |
| `ISLETME.isletme_turu` | `KAFE` | Café. |
| `ISLETME.isletme_turu` | `RESTORAN` | Restaurant. |

Java enum constants use the database values verbatim.
