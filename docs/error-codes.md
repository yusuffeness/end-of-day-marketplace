# Error codes

Every API error is returned as an RFC 9457 problem detail (`application/problem+json`). In addition to the standard fields, each response carries a `code` property with a stable identifier in the format `EOD-<number>`. The `detail` field is a user-facing message in Turkish.

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Bu işlem için giriş yapmalısınız.",
  "code": "EOD-401"
}
```

## General codes

| Code | HTTP status | When | Detail |
| --- | --- | --- | --- |
| `EOD-400` | 400 Bad Request | The request body or parameters are malformed or fail validation. Validation failures list each failed field rule in the `errors` property (see [api.md](api.md#errors)). | Gönderilen bilgiler geçersiz. |
| `EOD-401` | 401 Unauthorized | The request requires a signed-in user, or the signed-in account was deactivated or removed since the last request (the session is then invalidated). | Bu işlem için giriş yapmalısınız. |
| `EOD-403` | 403 Forbidden | The signed-in user is not allowed to perform the action, or the CSRF token is missing or invalid. | Bu işlem için yetkiniz yok. |
| `EOD-404` | 404 Not Found | The requested path or resource does not exist. | İstenen kaynak bulunamadı. |
| `EOD-500` | 500 Internal Server Error | An unexpected error occurred. The cause is logged on the server and is not included in the response. | Beklenmeyen bir hata oluştu. |

## Business rule codes

EOD-001 to EOD-399 are reserved for business rules raised by the database and services.

The database raises these codes from triggers with `SIGNAL SQLSTATE '45000'`; the message text starts with the code followed by a colon and the Turkish message, for example `EOD-002: Bir siparişte birden fazla başarılı tahsilat olamaz.`

| Code | Rule | Raised by | Report section |
| --- | --- | --- | --- |
| `EOD-001` | The listing of an order item belongs to the business of the order; the business of an order that has items cannot change. | `trg_siparis_kalemi_before_insert`, `trg_siparis_kalemi_before_update`, `trg_siparis_before_update` | 5.1 |
| `EOD-002` | An order has at most one successful charge, also when a failed charge is updated to successful. | `trg_odeme_islemi_before_insert`, `trg_odeme_islemi_before_update` | 5.1 |
| `EOD-003` | A refund refers to a successful charge (TAHSILAT, BASARILI) of the same order. | `trg_odeme_islemi_before_insert`, `trg_odeme_islemi_before_update` | 5.1 |
| `EOD-004` | The successful refunds of a charge never add up to more than the charge; failed refunds are not counted. | `trg_odeme_islemi_before_insert`, `trg_odeme_islemi_before_update` | 5.1 |
| `EOD-005` | A status change goes from ONAYLANDI to TESLIM_EDILDI, IPTAL_EDILDI or TESLIM_ALINMADI; inserts are not checked. | `trg_siparis_before_update` | 5.1 |
| `EOD-006` | Only a delivered order (TESLIM_EDILDI) can be reviewed. | `trg_degerlendirme_before_insert` | 5.1 |
| `EOD-007` | A listing is published (AKTIF) only for an approved and active business; updates are checked only when the status changes to AKTIF. | `trg_paket_ilani_before_insert`, `trg_paket_ilani_before_update` | 5.1 |
| `EOD-008` | Only the owner of the order's business verifies the pickup. | `trg_teslim_alma_before_insert`, `trg_teslim_alma_before_update` | 5.1 |

A trigger error reaches the client as a problem detail with the trigger's code and Turkish message (`DatabaseRuleViolation`): `EOD-008` with 403 Forbidden, every other code with 409 Conflict.

The services check EOD-001 and EOD-005 to EOD-008 before writing, so that the client gets a precise answer; they use the same code with a detail that names the action, for example "Yalnızca onaylanmış bir sipariş iptal edilebilir." for EOD-005 on POST /orders/{id}/cancel. The triggers stay the final guarantee.

## Application codes

The services raise these codes; the database does not.

| Code | Rule | Raised by | HTTP status |
| --- | --- | --- | --- |
| `EOD-009` | An email address belongs to at most one account, regardless of letter case. Detail: "Bu e-posta adresiyle kayıtlı bir hesap var." | `RegistrationService` (POST /auth/register) | 409 Conflict |
| `EOD-010` | Sign-in requires a known email address and the matching password; both failures give the same answer. Detail: "E-posta veya parola hatalı." | `LoginService` (POST /auth/login) | 401 Unauthorized |
| `EOD-011` | An inactive account cannot sign in; reported only after the password matched. Detail: "Hesabınız pasif durumda." | `LoginService` (POST /auth/login) | 403 Forbidden |
| `EOD-012` | Changing the password requires the current password. Detail: "Mevcut parola hatalı." | `UserService` (PUT /me/password) | 400 Bad Request |
| `EOD-013` | An order is accepted only for an approved and active business and active listings. Detail: "İlan şu anda satışta değil." | `OrderService` (POST /orders) | 409 Conflict |
| `EOD-014` | A listing must have at least the ordered number of units left, checked on the locked row. Detail: "İlanda yeterli adet kalmadı." | `OrderService` (POST /orders) | 409 Conflict |
| `EOD-015` | The pickup windows of the listings of an order must have a common time (report 4.4). Detail: "Seçilen ilanların ortak bir teslim alma aralığı yok." | `OrderService` (POST /orders) | 409 Conflict |
| `EOD-016` | An order is placed before the common pickup window starts (report 5.1). Detail: "Teslim alma aralığı başladığı için sipariş verilemez." | `OrderService` (POST /orders) | 409 Conflict |
| `EOD-017` | The simulated payment provider declined the charge; nothing is stored. Detail: "Ödeme reddedildi, sipariş oluşturulmadı." | `OrderService` (POST /orders) | 402 Payment Required |
| `EOD-018` | A request id is processed once and belongs to one request: an order charge of one customer, or a refund of one order. Detail: "Bu istek numarası başka bir işlem için kullanılmış." | `OrderService` (POST /orders, POST /orders/{id}/cancel) | 409 Conflict |
| `EOD-019` | The customer cancels only before the pickup window starts (report 5.1). Detail: "Teslim alma aralığı başladığı için sipariş iptal edilemez." | `OrderService` (POST /orders/{id}/cancel) | 409 Conflict |
| `EOD-020` | The simulated payment provider declined the refund; the refund is kept as BASARISIZ and the order stays confirmed. Detail: "İade reddedildi, sipariş iptal edilmedi." | `OrderService` (POST /orders/{id}/cancel) | 402 Payment Required |
| `EOD-021` | A pickup code is verified only for a confirmed order of the business in the path. Detail: "Bu işletmede bu koda ait onaylanmış bir sipariş yok." | `PickupService` (POST /businesses/{id}/pickups/verify) | 404 Not Found |
| `EOD-022` | A pickup is verified only inside the pickup window, both ends included (report 5.1). Detail: "Teslim yalnızca teslim alma aralığında yapılabilir." | `PickupService` (POST /businesses/{id}/pickups/verify) | 409 Conflict |
| `EOD-023` | A pickup code is used only once (report 5.1). Detail: "Bu teslim kodu daha önce kullanıldı." | `PickupService` (POST /businesses/{id}/pickups/verify) | 409 Conflict |
| `EOD-024` | An order is reviewed at most once (report 4.5). Detail: "Bu sipariş zaten değerlendirildi." | `ReviewService` (POST /orders/{id}/review) | 409 Conflict |
| `EOD-025` | The pickup window of a new listing starts before it ends and ends in the future. Detail: "Teslim alma aralığının başlangıcı bitişinden önce olmalı ve bitişi gelecekte olmalıdır." | `ListingService` (POST /businesses/{id}/listings) | 400 Bad Request |
