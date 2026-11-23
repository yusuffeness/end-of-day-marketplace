# Constraint tests

Every single-table constraint created by `V1__create_schema.sql` is proven by an integration test in
`backend/src/test/java/tr/edu/etu/endofday/schema/`. Each rule has at least one valid insert that must succeed and one
invalid insert that must fail. A failure is accepted only when MySQL reports the expected error code and its message
names the violated constraint, key or column.

| Error code | MySQL name | Meaning |
| --- | --- | --- |
| 3819 | ER_CHECK_CONSTRAINT_VIOLATED | A CHECK constraint is violated |
| 1062 | ER_DUP_ENTRY | A primary key or UNIQUE constraint is violated |
| 1452 | ER_NO_REFERENCED_ROW_2 | A foreign key points to a missing row |
| 1451 | ER_ROW_IS_REFERENCED_2 | A referenced row cannot be deleted |
| 1048 | ER_BAD_NULL_ERROR | NULL written to a NOT NULL column |
| - | - | Valid insert, must succeed |

Methods marked with (P) are parameterized; the values they run with are listed in the rule column. Methods marked
with (O) run through the eod_owner connection; all others run as the application account eod_app.

## Schema shape

`SchemaMatchesReportIntegrationTest` guards the schema against drift from the project report.

| Table | Rule | Test method | Expected error code |
| --- | --- | --- | --- |
| all | Exactly the ten report tables exist besides `flyway_schema_history` | `databaseContainsExactlyTheReportTables` | - |
| all | Column names and order match the report | `everyTableHasExactlyTheReportColumnsInTheReportOrder` | - |
| ROL | Exactly the roles MUSTERI, ISLETME_YETKILISI, YONETICI exist | `roleTableHoldsExactlyTheThreeInitialRoles` | - |

## Constraints

All methods below belong to `SchemaConstraintsIntegrationTest`.

| Table | Rule | Test method | Expected error code |
| --- | --- | --- | --- |
| KULLANICI | `telefon` is optional | `acceptsUserWithoutPhoneNumber` | - |
| KULLANICI | NOT NULL: ad, soyad, eposta, parola_ozeti, aktif_mi, kayit_zamani | `rejectsUserWithoutRequiredValue` (P) | 1048 |
| KULLANICI | `uq_kullanici_eposta`: different addresses are stored | `acceptsUsersWithDifferentEmailAddresses` | - |
| KULLANICI | `uq_kullanici_eposta`: identical address | `rejectsDuplicateEmail` | 1062 |
| KULLANICI | `uq_kullanici_eposta`: 'Ali@ornek.com' then 'ali@ornek.com' | `rejectsEmailThatDiffersOnlyInLetterCase` | 1062 |
| KULLANICI | `uq_kullanici_eposta`: 'ALI@ORNEK.COM' then 'ali@ornek.com' (I/i) | `rejectsEmailThatDiffersOnlyInCapitalLetterI` | 1062 |
| ROL | `uq_rol_rol_adi`: a new name is stored | `acceptsNewRoleName` | - |
| ROL | `uq_rol_rol_adi`: existing name 'MUSTERI' | `rejectsDuplicateRoleName` | 1062 |
| ROL | `uq_rol_rol_adi` is case-sensitive (utf8mb4_bin): 'musteri' is stored next to 'MUSTERI' | `storesLowercaseRoleNameSeparatelyFromUppercaseCode` | - |
| ROL | NOT NULL: rol_adi | `rejectsRoleWithoutName` | 1048 |
| KULLANICI_ROL | `pk_kullanici_rol`: one user holds several roles | `acceptsSeveralRolesForOneUser` | - |
| KULLANICI_ROL | `pk_kullanici_rol`: the same role twice | `rejectsSameRoleTwiceForOneUser` | 1062 |
| KULLANICI_ROL | NOT NULL: kullanici_id, rol_id | `rejectsUserRoleWithoutRequiredValue` (P) | 1048 |
| KULLANICI_ROL | `fk_kullanici_rol_kullanici_id`, `fk_kullanici_rol_rol_id` | `rejectsUserRoleReferencingMissingRow` (P) | 1452 |
| KULLANICI_ROL | `fk_kullanici_rol_kullanici_id` ON DELETE CASCADE | `deletingUserAlsoDeletesTheirRoleAssignments` (O) | - |
| SIPARIS | `fk_siparis_musteri_id` ON DELETE RESTRICT | `rejectsDeletingUserWhoHasOrders` (O) | 1451 |
| ISLETME | `chk_isletme_isletme_turu`: FIRIN, KAFE, RESTORAN | `acceptsEveryBusinessType` (P) | - |
| ISLETME | `chk_isletme_isletme_turu`: 'MARKET' | `rejectsUnknownBusinessType` | 3819 |
| ISLETME | `chk_isletme_isletme_turu` is case-sensitive: 'kafe' | `rejectsLowercaseBusinessType` | 3819 |
| ISLETME | `chk_isletme_onay_durumu`: BEKLEMEDE, ONAYLANDI, REDDEDILDI | `acceptsEveryApprovalStatus` (P) | - |
| ISLETME | `chk_isletme_onay_durumu`: 'ASKIDA' | `rejectsUnknownApprovalStatus` | 3819 |
| ISLETME | `chk_isletme_onay_durumu` is case-sensitive: 'beklemede' | `rejectsLowercaseApprovalStatus` | 3819 |
| ISLETME | NOT NULL: yetkili_id, isletme_adi, isletme_turu, sehir, ilce, adres, telefon, onay_durumu, aktif_mi | `rejectsBusinessWithoutRequiredValue` (P) | 1048 |
| ISLETME | `fk_isletme_yetkili_id` | `rejectsBusinessReferencingMissingOwner` | 1452 |
| PAKET_ILANI | `chk_paket_ilani_durum`: AKTIF, PASIF | `acceptsEveryListingStatus` (P) | - |
| PAKET_ILANI | `chk_paket_ilani_durum`: 'SILINDI' | `rejectsUnknownListingStatus` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_durum` is case-sensitive: 'aktif' | `rejectsLowercaseListingStatus` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_aciklama_not_blank`: '' and '   ' | `rejectsBlankDescription` (P) | 3819 |
| PAKET_ILANI | `chk_paket_ilani_alerjen_bilgisi_not_blank`: '' and '   ' | `rejectsBlankAllergenInformation` (P) | 3819 |
| PAKET_ILANI | `chk_paket_ilani_alerjen_bilgisi_not_blank`: explicit "no known allergens" text | `acceptsExplicitStatementThatThereAreNoKnownAllergens` | - |
| PAKET_ILANI | `chk_paket_ilani_toplam_adet_positive`: toplam_adet = 1 | `acceptsTotalQuantityOfOne` | - |
| PAKET_ILANI | `chk_paket_ilani_toplam_adet_positive`: toplam_adet = 0 | `rejectsZeroTotalQuantity` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_kalan_adet_within_total`: kalan_adet = toplam_adet | `acceptsRemainingQuantityEqualToTotal` | - |
| PAKET_ILANI | `chk_paket_ilani_kalan_adet_within_total`: kalan_adet = 0 | `acceptsZeroRemainingQuantity` | - |
| PAKET_ILANI | `chk_paket_ilani_kalan_adet_within_total`: kalan_adet = toplam_adet + 1 | `rejectsRemainingQuantityAboveTotal` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_satis_fiyati_discounted`: 99.99 against 100.00 | `acceptsSalePriceOneCentBelowNormalPrice` | - |
| PAKET_ILANI | `chk_paket_ilani_satis_fiyati_discounted`: satis_fiyati = 0.01 | `acceptsSalePriceOfOneCent` | - |
| PAKET_ILANI | `chk_paket_ilani_satis_fiyati_discounted`: satis_fiyati = normal_fiyat | `rejectsSalePriceEqualToNormalPrice` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_satis_fiyati_discounted`: 100.01 against 100.00 | `rejectsSalePriceAboveNormalPrice` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_satis_fiyati_discounted`: satis_fiyati = 0.00 | `rejectsZeroSalePrice` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_teslim_window`: one-minute window | `acceptsListingPickupWindowOfOneMinute` | - |
| PAKET_ILANI | `chk_paket_ilani_teslim_window`: start = end | `rejectsListingPickupWindowThatEndsWhenItStarts` | 3819 |
| PAKET_ILANI | `chk_paket_ilani_teslim_window`: end before start | `rejectsListingPickupWindowThatEndsBeforeItStarts` | 3819 |
| PAKET_ILANI | NOT NULL: isletme_id, baslik, aciklama, alerjen_bilgisi, normal_fiyat, satis_fiyati, toplam_adet, kalan_adet, teslim_baslangic, teslim_bitis, durum, olusturulma_zamani | `rejectsListingWithoutRequiredValue` (P) | 1048 |
| PAKET_ILANI | `fk_paket_ilani_isletme_id` | `rejectsListingReferencingMissingBusiness` | 1452 |
| SIPARIS | `chk_siparis_durum`, `chk_siparis_iptal_zamani`: ONAYLANDI, TESLIM_EDILDI, TESLIM_ALINMADI without iptal_zamani | `acceptsOrderThatIsNotCancelledWithoutCancellationTime` (P) | - |
| SIPARIS | `chk_siparis_iptal_zamani`: IPTAL_EDILDI with iptal_zamani | `acceptsCancelledOrderWithCancellationTime` | - |
| SIPARIS | `chk_siparis_durum`: 'BEKLEMEDE' | `rejectsUnknownOrderStatus` | 3819 |
| SIPARIS | `chk_siparis_durum` is case-sensitive: 'onaylandi' | `rejectsLowercaseOrderStatus` | 3819 |
| SIPARIS | `chk_siparis_iptal_zamani`: IPTAL_EDILDI without iptal_zamani | `rejectsCancelledOrderWithoutCancellationTime` | 3819 |
| SIPARIS | `chk_siparis_iptal_zamani`: ONAYLANDI, TESLIM_EDILDI, TESLIM_ALINMADI with iptal_zamani | `rejectsCancellationTimeOnOrderThatIsNotCancelled` (P) | 3819 |
| SIPARIS | NOT NULL: musteri_id, isletme_id, olusturulma_zamani, durum | `rejectsOrderWithoutRequiredValue` (P) | 1048 |
| SIPARIS | `fk_siparis_musteri_id`, `fk_siparis_isletme_id` | `rejectsOrderReferencingMissingRow` (P) | 1452 |
| SIPARIS_KALEMI | `chk_siparis_kalemi_adet_positive`, `chk_siparis_kalemi_birim_fiyat_positive`: adet = 1, birim_fiyat = 0.01 | `acceptsOrderItemWithQuantityOfOneAndUnitPriceOfOneCent` | - |
| SIPARIS_KALEMI | `chk_siparis_kalemi_adet_positive`: adet = 0 | `rejectsOrderItemWithZeroQuantity` | 3819 |
| SIPARIS_KALEMI | `chk_siparis_kalemi_birim_fiyat_positive`: birim_fiyat = 0.00 | `rejectsOrderItemWithZeroUnitPrice` | 3819 |
| SIPARIS_KALEMI | `pk_siparis_kalemi`: the same listing twice in one order | `rejectsSameListingTwiceInOneOrder` | 1062 |
| SIPARIS_KALEMI | NOT NULL: siparis_id, ilan_id, adet, birim_fiyat | `rejectsOrderItemWithoutRequiredValue` (P) | 1048 |
| SIPARIS_KALEMI | `fk_siparis_kalemi_siparis_id`, `fk_siparis_kalemi_ilan_id` | `rejectsOrderItemReferencingMissingRow` (P) | 1452 |
| ODEME_ISLEMI | `chk_odeme_islemi_kaynak_islem`: TAHSILAT without source | `acceptsChargeWithoutSourceTransaction` | - |
| ODEME_ISLEMI | `chk_odeme_islemi_kaynak_islem`: IADE with source | `acceptsRefundThatReferencesItsCharge` | - |
| ODEME_ISLEMI | `chk_odeme_islemi_kaynak_islem`: TAHSILAT with source | `rejectsChargeThatReferencesSourceTransaction` | 3819 |
| ODEME_ISLEMI | `chk_odeme_islemi_kaynak_islem`: IADE without source | `rejectsRefundWithoutSourceTransaction` | 3819 |
| ODEME_ISLEMI | `chk_odeme_islemi_islem_turu`: 'DUZELTME' | `rejectsUnknownTransactionType` | 3819 |
| ODEME_ISLEMI | `chk_odeme_islemi_islem_turu` is case-sensitive: 'tahsilat' | `rejectsLowercaseTransactionType` | 3819 |
| ODEME_ISLEMI | `chk_odeme_islemi_durum`: BASARILI, BASARISIZ | `acceptsEveryPaymentResult` (P) | - |
| ODEME_ISLEMI | `chk_odeme_islemi_durum`: 'BEKLIYOR' | `rejectsUnknownPaymentResult` | 3819 |
| ODEME_ISLEMI | `chk_odeme_islemi_durum` is case-sensitive: 'basarili' | `rejectsLowercasePaymentResult` | 3819 |
| ODEME_ISLEMI | `chk_odeme_islemi_tutar_positive`: tutar = 0.01 | `acceptsPaymentAmountOfOneCent` | - |
| ODEME_ISLEMI | `chk_odeme_islemi_tutar_positive`: tutar = 0.00 | `rejectsZeroPaymentAmount` | 3819 |
| ODEME_ISLEMI | `uq_odeme_islemi_istek_no`: the same request number twice | `rejectsDuplicateRequestNumber` | 1062 |
| ODEME_ISLEMI | NOT NULL: siparis_id, istek_no, islem_turu, tutar, durum, islem_zamani | `rejectsPaymentWithoutRequiredValue` (P) | 1048 |
| ODEME_ISLEMI | `fk_odeme_islemi_siparis_id` | `rejectsPaymentReferencingMissingOrder` | 1452 |
| ODEME_ISLEMI | `fk_odeme_islemi_kaynak_islem_id` | `rejectsRefundReferencingMissingSourceTransaction` | 1452 |
| TESLIM_ALMA | `chk_teslim_alma_verification`: neither time nor verifier | `acceptsPickupAwaitingVerification` | - |
| TESLIM_ALMA | `chk_teslim_alma_verification`: both time and verifier | `acceptsVerifiedPickupWithTimeAndVerifier` | - |
| TESLIM_ALMA | `chk_teslim_alma_verification`: time without verifier | `rejectsPickupTimeWithoutVerifier` | 3819 |
| TESLIM_ALMA | `chk_teslim_alma_verification`: verifier without time | `rejectsVerifierWithoutPickupTime` | 3819 |
| TESLIM_ALMA | `chk_teslim_alma_teslim_kodu_format`: '000123' | `acceptsPickupCodeOfSixDigitsWithLeadingZeros` | - |
| TESLIM_ALMA | `chk_teslim_alma_teslim_kodu_format`: '12345', '12a456', 'ABCDEF', '12 456' | `rejectsPickupCodeThatIsNotSixDigits` (P) | 3819 |
| TESLIM_ALMA | `uq_teslim_alma_teslim_kodu`: the same code for two orders | `rejectsDuplicatePickupCode` | 1062 |
| TESLIM_ALMA | `pk_teslim_alma`: a second pickup for the same order | `rejectsSecondPickupForSameOrder` | 1062 |
| TESLIM_ALMA | `chk_teslim_alma_teslim_window`: one-minute window | `acceptsPickupWindowOfOneMinute` | - |
| TESLIM_ALMA | `chk_teslim_alma_teslim_window`: start = end | `rejectsPickupWindowThatEndsWhenItStarts` | 3819 |
| TESLIM_ALMA | `chk_teslim_alma_teslim_window`: end before start | `rejectsPickupWindowThatEndsBeforeItStarts` | 3819 |
| TESLIM_ALMA | NOT NULL: siparis_id, teslim_kodu, teslim_baslangic, teslim_bitis | `rejectsPickupWithoutRequiredValue` (P) | 1048 |
| TESLIM_ALMA | `fk_teslim_alma_siparis_id` | `rejectsPickupReferencingMissingOrder` | 1452 |
| TESLIM_ALMA | `fk_teslim_alma_dogrulayan_id` | `rejectsPickupVerifiedByMissingUser` | 1452 |
| DEGERLENDIRME | `chk_degerlendirme_puan_range`: puan 1 and 5 | `acceptsRatingsAtBothEndsOfTheScale` (P) | - |
| DEGERLENDIRME | `chk_degerlendirme_puan_range`: puan 0 and 6 | `rejectsRatingOutsideOneToFive` (P) | 3819 |
| DEGERLENDIRME | `yorum` is optional | `acceptsReviewWithoutComment` | - |
| DEGERLENDIRME | `pk_degerlendirme`: a second review for the same order | `rejectsSecondReviewForSameOrder` | 1062 |
| DEGERLENDIRME | NOT NULL: siparis_id, puan, olusturulma_zamani | `rejectsReviewWithoutRequiredValue` (P) | 1048 |
| DEGERLENDIRME | `fk_degerlendirme_siparis_id` | `rejectsReviewReferencingMissingOrder` | 1452 |

## Delete tests

`deletingUserAlsoDeletesTheirRoleAssignments` and `rejectsDeletingUserWhoHasOrders` prove the ON DELETE behaviour of
foreign keys, which needs a KULLANICI row to be deleted. The application never deletes users physically (report 5.2),
so eod_app has no DELETE privilege and is not given one for these tests. They run through a separate eod_owner
connection (`support/OwnerJdbc`) that exists only for schema tests and is never used by application code. That
connection commits every statement, so both tests run outside the test transaction, create their own rows and remove
them in a `finally` block, leaving nothing behind for other tests.

Rules that span several tables (for example that an order item belongs to a listing of the same business, or that
refunds never exceed the charge) are enforced by later migrations and tested separately.
