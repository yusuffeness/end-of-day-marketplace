# Constraint tests

This document maps every database rule to the integration test that proves it. All tests live in
`backend/src/test/java/tr/edu/etu/endofday/schema/`.

## Single-table constraints

Every single-table constraint created by `V1__create_schema.sql` is proven by `SchemaConstraintsIntegrationTest`. Each
rule has at least one valid insert that must succeed and one
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

The rows used by these tests also satisfy the cross-table rules of the triggers (for example, reviews are written for
delivered orders and pickups are verified by the business owner), so that each test fails only for the constraint it
is about.

### Schema shape

`SchemaMatchesReportIntegrationTest` guards the schema against drift from the project report.

| Table | Rule | Test method | Expected error code |
| --- | --- | --- | --- |
| all | Exactly the ten report tables exist besides `flyway_schema_history` | `databaseContainsExactlyTheReportTables` | - |
| all | Column names and order match the report | `everyTableHasExactlyTheReportColumnsInTheReportOrder` | - |
| ROL | Exactly the roles MUSTERI, ISLETME_YETKILISI, YONETICI exist | `roleTableHoldsExactlyTheThreeInitialRoles` | - |

### Constraints

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

### Delete tests

`deletingUserAlsoDeletesTheirRoleAssignments` and `rejectsDeletingUserWhoHasOrders` prove the ON DELETE behaviour of
foreign keys, which needs a KULLANICI row to be deleted. The application never deletes users physically (report 5.2),
so eod_app has no DELETE privilege on KULLANICI and is not given one for these tests. They run through a separate eod_owner
connection (`support/OwnerJdbc`) that exists only for schema tests and is never used by application code. That
connection commits every statement, so both tests run outside the test transaction, create their own rows and remove
them in a `finally` block, leaving nothing behind for other tests.

## Trigger rules

`TriggerRulesIntegrationTest` proves the cross-table rules of `V2__create_triggers.sql` as eod_app, each test in its own
rolled-back transaction. A rejection is accepted only with SQLSTATE 45000 and a message that starts with the expected
code (see [error-codes.md](error-codes.md)).

| Code | Rule | Test method | Expected result |
| --- | --- | --- | --- |
| EOD-001 | Item whose listing belongs to the order's business | `acceptsOrderItemFromListingOfTheOrdersBusiness` | - |
| EOD-001 | Item whose listing belongs to another business | `rejectsOrderItemFromListingOfAnotherBusiness` | 45000 EOD-001 |
| EOD-001 | Item changed to another listing of the same business | `acceptsChangingOrderItemToAnotherListingOfTheSameBusiness` | - |
| EOD-001 | Item changed to a listing of another business | `rejectsChangingOrderItemToListingOfAnotherBusiness` | 45000 EOD-001 |
| EOD-001 | Business of an order without items changes | `acceptsChangingBusinessOfOrderWithoutItems` | - |
| EOD-001 | Business of an order with items changes | `rejectsChangingBusinessOfOrderWithItems` | 45000 EOD-001 |
| EOD-002 | First successful charge | `acceptsFirstSuccessfulCharge` | - |
| EOD-002 | Second successful charge | `rejectsSecondSuccessfulCharge` | 45000 EOD-002 |
| EOD-002 | Failed charge next to a successful one | `acceptsFailedChargeNextToSuccessfulCharge` | - |
| EOD-002 | Successful charge after a failed one | `acceptsSuccessfulChargeAfterFailedCharge` | - |
| EOD-002 | Update BASARISIZ to BASARILI, no other successful charge | `acceptsTurningFailedChargeIntoSuccessWhenOrderHasNoOtherSuccessfulCharge` | - |
| EOD-002 | Update BASARISIZ to BASARILI, order already charged | `rejectsTurningFailedChargeIntoSuccessWhenOrderIsAlreadyCharged` | 45000 EOD-002 |
| EOD-003 | Refund of a successful charge of the same order | `acceptsRefundOfSuccessfulChargeOfTheSameOrder` | - |
| EOD-003 | Refund of a charge of another order | `rejectsRefundOfChargeOfAnotherOrder` | 45000 EOD-003 |
| EOD-003 | Refund of a failed charge | `rejectsRefundOfFailedCharge` | 45000 EOD-003 |
| EOD-003 | Refund of another refund | `rejectsRefundOfAnotherRefund` | 45000 EOD-003 |
| EOD-003 | Update moves a refund to a charge of another order | `rejectsMovingRefundToChargeOfAnotherOrder` | 45000 EOD-003 |
| EOD-004 | Refunds 25.00 + 15.00 of a 40.00 charge (exactly equal) | `acceptsRefundsThatAddUpExactlyToTheCharge` | - |
| EOD-004 | Refunds 25.00 + 15.01 of a 40.00 charge (0.01 over) | `rejectsRefundsThatExceedTheChargeByOneCent` | 45000 EOD-004 |
| EOD-004 | A failed 40.00 refund is not counted before a successful 40.00 refund | `ignoresFailedRefundsInTheRefundTotal` | - |
| EOD-004 | Failed refund on a fully refunded charge | `acceptsFailedRefundEvenWhenItWouldExceedTheCharge` | - |
| EOD-004 | Update BASARISIZ to BASARILI pushes the total 0.01 over | `rejectsTurningFailedRefundIntoSuccessWhenTotalWouldExceedTheCharge` | 45000 EOD-004 |
| EOD-004 | Update raises a full refund to 40.01 | `rejectsRaisingSuccessfulRefundAboveTheCharge` | 45000 EOD-004 |
| EOD-004 | Update of a full refund does not count the refund twice | `acceptsUpdatingFullRefundWithoutCountingItTwice` | - |
| EOD-005 | ONAYLANDI to TESLIM_EDILDI, IPTAL_EDILDI, TESLIM_ALINMADI | `acceptsEveryTransitionFromConfirmedToFinalState` (P) | - |
| EOD-005 | ONAYLANDI to ONAYLANDI (status unchanged) | `acceptsUpdateThatKeepsConfirmedStatus` | - |
| EOD-005 | TESLIM_EDILDI to IPTAL_EDILDI | `rejectsCancellingDeliveredOrder` | 45000 EOD-005 |
| EOD-005 | TESLIM_EDILDI, IPTAL_EDILDI, TESLIM_ALINMADI to ONAYLANDI; TESLIM_ALINMADI, IPTAL_EDILDI to TESLIM_EDILDI | `rejectsLeavingFinalState` (P) | 45000 EOD-005 |
| EOD-005 | Insert in TESLIM_EDILDI, IPTAL_EDILDI, TESLIM_ALINMADI (inserts are not checked) | `acceptsInsertingOrderInFinalState` (P) | - |
| EOD-006 | Review of a TESLIM_EDILDI order | `acceptsReviewOfDeliveredOrder` | - |
| EOD-006 | Review of an ONAYLANDI, IPTAL_EDILDI, TESLIM_ALINMADI order | `rejectsReviewOfOrderThatIsNotDelivered` (P) | 45000 EOD-006 |
| EOD-007 | AKTIF listing of an approved, active business | `acceptsActiveListingOfApprovedActiveBusiness` | - |
| EOD-007 | AKTIF listing of a BEKLEMEDE, REDDEDILDI business | `rejectsActiveListingOfBusinessThatIsNotApproved` (P) | 45000 EOD-007 |
| EOD-007 | AKTIF listing of an approved but inactive business | `rejectsActiveListingOfInactiveBusiness` | 45000 EOD-007 |
| EOD-007 | PASIF listing of a business that is not approved | `acceptsPassiveListingOfBusinessThatIsNotApproved` | - |
| EOD-007 | Update PASIF to AKTIF, approved and active business | `acceptsActivatingListingOfApprovedActiveBusiness` | - |
| EOD-007 | Update PASIF to AKTIF, business not approved | `rejectsActivatingListingOfBusinessThatIsNotApproved` | 45000 EOD-007 |
| EOD-007 | kalan_adet decreased while the business is inactive (status unchanged) | `acceptsDecreasingRemainingQuantityWhileBusinessIsInactive` | - |
| EOD-008 | Insert verified by the business owner | `acceptsPickupVerifiedByBusinessOwner` | - |
| EOD-008 | Insert verified by another user (the customer) | `rejectsPickupVerifiedByAnotherUser` | 45000 EOD-008 |
| EOD-008 | Insert without verifier | `acceptsPickupWithoutVerifier` | - |
| EOD-008 | Update verifies by the business owner | `acceptsVerifyingPickupLaterByBusinessOwner` | - |
| EOD-008 | Update verifies by another user | `rejectsVerifyingPickupLaterByAnotherUser` | 45000 EOD-008 |

## Trigger concurrency

`TriggerConcurrencyIntegrationTest` runs two conflicting inserts for the same order on two eod_app connections,
released at the same moment with a `CountDownLatch`. Each insert commits on its own, so the class is not
transactional; every repetition creates its own rows and removes them through the eod_owner connection. Each test is
repeated 10 times with the same expected result.

| Code | Rule | Test method | Expected result |
| --- | --- | --- | --- |
| EOD-002 | Two concurrent successful charges | `acceptsExactlyOneOfTwoConcurrentSuccessfulCharges` (10x) | one stored, the other 45000 EOD-002 |
| EOD-004 | Two concurrent refunds of 60% of the charge each | `acceptsExactlyOneOfTwoConcurrentRefundsThatTogetherExceedTheCharge` (10x) | one stored, the other 45000 EOD-004 |

## Not-picked-up procedure and event

`NotPickedUpProcedureIntegrationTest` calls `proc_mark_not_picked_up()` as eod_app in a rolled-back transaction.
Pickup windows are placed relative to the application clock. The event definition is read through the eod_owner
connection, because `information_schema.EVENTS` lists events only to accounts with the EVENT privilege.

| Object | Rule | Test method | Expected result |
| --- | --- | --- | --- |
| proc_mark_not_picked_up | ONAYLANDI order whose window ended becomes TESLIM_ALINMADI | `marksConfirmedOrderWhosePickupWindowHasEndedAsNotPickedUp` | TESLIM_ALINMADI |
| proc_mark_not_picked_up | kalan_adet unchanged and no new ODEME_ISLEMI row | `leavesStockAndPaymentsUntouchedWhenMarkingOrderAsNotPickedUp` | unchanged |
| proc_mark_not_picked_up | ONAYLANDI order whose window has not ended | `leavesConfirmedOrderWhosePickupWindowHasNotEndedUnchanged` | ONAYLANDI |
| proc_mark_not_picked_up | TESLIM_EDILDI and IPTAL_EDILDI orders whose window ended | `leavesDeliveredAndCancelledOrdersUnchanged` | unchanged |
| evt_mark_not_picked_up | ENABLED, EVERY 5 MINUTE, ON COMPLETION PRESERVE, calls the procedure | `eventRunsTheProcedureEveryFiveMinutes` | - |

## Report views

`ReportViewsIntegrationTest` loads a small data set that can be checked by hand (described in the class comment) and
reads the views as eod_app in a rolled-back transaction.

| View | Rule | Test method | Expected result |
| --- | --- | --- | --- |
| v_order_summary | kalem_sayisi and toplam_tutar per order, including an order without items | `orderSummaryCountsItemsAndTotalsEveryOrder` | hand-computed rows |
| v_business_sales | One row per month; cancelled orders count in siparis_sayisi and iptal_sayisi but not in satilan_adet | `businessSalesGroupsByMonthAndExcludesCancelledUnits` | hand-computed rows |
| v_payment_summary | Successful charge, successful refunds only, net_tutar = tahsilat_tutari - iade_tutari | `paymentSummaryNetsSuccessfulRefundsAgainstTheSuccessfulCharge` | hand-computed rows |
| all | Exactly the three report views exist | `schemaContainsExactlyTheThreeReportViews` | - |
| all | Column names and order of each view | `viewsHaveExactlyTheDocumentedColumns` | - |
| all | No ad, soyad, eposta, telefon, parola_ozeti or adres column | `viewsExposeNoPersonalColumns` | - |

## Privileges

`PrivilegesIntegrationTest` proves the account privileges of the init script and `V5__grant_table_privileges.sql`.
eod_report statements run through a separate test-only connection (`support/ReportJdbc`). Refused DDL statements run
outside a transaction, because MySQL commits implicitly around DDL. 1142 is ER_TABLEACCESS_DENIED_ERROR.

| Account | Rule | Test method | Expected error code |
| --- | --- | --- | --- |
| eod_app | DELETE on KULLANICI_ROL | `appAccountCanDeleteRoleAssignments` | - |
| eod_app | No DELETE on SIPARIS | `appAccountCannotDeleteOrders` | 1142 |
| eod_app | No DROP | `appAccountCannotDropTables` | 1142 |
| eod_app | No ALTER | `appAccountCannotAlterTables` | 1142 |
| eod_app | No GRANT | `appAccountCannotGrantPrivileges` | 1142 |
| eod_report | SELECT on v_order_summary, v_business_sales, v_payment_summary | `reportAccountCanReadEveryReportView` (P) | - |
| eod_report | No SELECT on KULLANICI, SIPARIS | `reportAccountCannotReadBaseTables` (P) | 1142 |
