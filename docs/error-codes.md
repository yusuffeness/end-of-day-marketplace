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
| `EOD-400` | 400 Bad Request | The request body or parameters are malformed or fail validation. | Gönderilen bilgiler geçersiz. |
| `EOD-401` | 401 Unauthorized | The request requires a signed-in user. | Bu işlem için giriş yapmalısınız. |
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
