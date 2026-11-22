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
