# API

The backend exposes a JSON API under `/api/v1`. This document is the contract for every endpoint of the application;
the **Status** column shows which endpoints are already implemented and which are planned. Runnable examples of the
implemented endpoints are in [api.http](api.http).

## Conventions

### Requests and responses

- Request and response bodies are JSON (`application/json`, UTF-8). Field names are English camelCase.
- Status and type values are the database codes, unchanged: `ONAYLANDI`, `FIRIN`, `MUSTERI` and so on (see
  [glossary.md](glossary.md)).
- Amounts are JSON **strings** with two decimal places, for example `"120.00"`, so that no client converts them to
  binary floating point. Amount query parameters use the same format (`minPrice=50.00`).
- Times are ISO-8601 local date-times in the Europe/Istanbul time zone without an offset, for example
  `"2026-10-14T19:00:00"`. Dates are ISO-8601 local dates, for example `2026-10-14`. A `from`/`to` date range includes
  both days.
- Text fields are trimmed. An optional text field that is empty after trimming is stored as `null`.
- Unknown request fields are ignored.

### Pagination

Endpoints that return a list are paginated with the query parameters `page` (zero-based, default `0`) and `size`
(default `20`, at most `100`). A value outside these bounds is rejected with `EOD-400`. The response has this shape:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0
}
```

### Authentication and CSRF

- Authentication is session based. A successful `POST /auth/login` returns the session cookie `JSESSIONID`
  (HttpOnly, SameSite=Lax). The session stores only the user id; the account's active flag and roles are read from the
  database on every request, so deactivating a user or revoking a role takes effect on the user's next request.
- Every state-changing request (`POST`, `PUT`, `DELETE`), including register, login and logout, needs a CSRF token.
  The server sets it in the `XSRF-TOKEN` cookie; the client sends the same value back in the `X-XSRF-TOKEN` header.
  Call `GET /auth/csrf` once before the first state-changing request to receive the cookie. Login and logout replace
  the token, so the client always reads the current cookie value before sending.
- Roles are `MUSTERI` (customer), `ISLETME_YETKILISI` (business owner) and `YONETICI` (administrator). Every registered
  user is a customer. In the tables below, **Role** names the role required; *public* means no sign-in is needed and
  *signed in* means any active user.
- "Owner of the business" means the signed-in user is `ISLETME.yetkili_id` of the business in the path and holds the
  `ISLETME_YETKILISI` role.

### Errors

Errors are RFC 9457 problem details (`application/problem+json`) with an additional `code` property and a Turkish
`detail`; all codes are listed in [error-codes.md](error-codes.md). Validation failures (`EOD-400`) also carry an
`errors` list with one entry per failed field rule:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Gönderilen bilgiler geçersiz.",
  "instance": "/api/v1/auth/register",
  "code": "EOD-400",
  "errors": [
    { "field": "email", "message": "Geçerli bir e-posta adresi giriniz." },
    { "field": "password", "message": "Parola en az bir harf ve bir rakam içermelidir." }
  ]
}
```

The following codes can be returned by every endpoint and are not repeated below: `EOD-400` for a malformed body or
invalid parameter, `EOD-401` when a sign-in is required (also after the account was deactivated), `EOD-403` when the
role is missing or the CSRF token is missing or invalid, `EOD-404` for an unknown path or resource, and `EOD-500`.

Error conditions of planned endpoints that have no code yet are marked *new code*; the code is assigned and added to
[error-codes.md](error-codes.md) when the endpoint is implemented.

## Endpoints

| Method | Path | Role | Status |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | public | implemented |
| POST | `/auth/register` | public | implemented |
| POST | `/auth/login` | public | implemented |
| POST | `/auth/logout` | public | implemented |
| GET | `/me` | signed in | implemented |
| PUT | `/me` | signed in | implemented |
| PUT | `/me/password` | signed in | implemented |
| GET | `/listings` | signed in | planned |
| GET | `/listings/{id}` | signed in | planned |
| POST | `/orders` | MUSTERI | planned |
| GET | `/orders` | MUSTERI | planned |
| GET | `/orders/{id}` | MUSTERI | planned |
| POST | `/orders/{id}/cancel` | MUSTERI | planned |
| POST | `/orders/{id}/review` | MUSTERI | planned |
| POST | `/businesses` | MUSTERI | planned |
| GET | `/businesses/mine` | MUSTERI | planned |
| PUT | `/businesses/{id}` | owner of the business | planned |
| POST | `/businesses/{id}/listings` | owner of the business | planned |
| PUT | `/businesses/{id}/listings/{listingId}/price` | owner of the business | planned |
| POST | `/businesses/{id}/listings/{listingId}/deactivate` | owner of the business | planned |
| GET | `/businesses/{id}/orders` | owner of the business | planned |
| POST | `/businesses/{id}/pickups/verify` | owner of the business | planned |
| GET | `/businesses/{id}/reports` | owner of the business | planned |
| GET | `/admin/business-applications` | YONETICI | planned |
| POST | `/admin/businesses/{id}/approve` | YONETICI | planned |
| POST | `/admin/businesses/{id}/reject` | YONETICI | planned |
| PUT | `/admin/users/{id}/active` | YONETICI | planned |
| PUT | `/admin/businesses/{id}/active` | YONETICI | planned |
| DELETE | `/admin/users/{id}/roles/{roleName}` | YONETICI | planned |
| POST | `/admin/listings/{id}/deactivate` | YONETICI | planned |
| GET | `/admin/reports/platform` | YONETICI | planned |

All paths are relative to `/api/v1`.

## Authentication

### GET /auth/csrf

Status: implemented. Role: public.

Makes sure the response sets the `XSRF-TOKEN` cookie. Spring Security creates the token lazily, so this endpoint reads
it explicitly; when the request already carries a valid token cookie, the cookie is kept.

Response: `204 No Content` with `Set-Cookie: XSRF-TOKEN=...` when the request had no token.

### POST /auth/register

Status: implemented. Role: public (CSRF token required).

Creates an active account with the role `MUSTERI`. The account and its role are written in one transaction. The email
address is trimmed and lower-cased; it is unique regardless of letter case. Registering does not sign the user in.

```json
{
  "firstName": "Ali",
  "lastName": "Yılmaz",
  "email": "ali@ornek.test",
  "phone": "0532 000 00 00",
  "password": "parola123"
}
```

| Field | Rule |
| --- | --- |
| `firstName`, `lastName` | required, at most 50 characters |
| `email` | required, valid email address, at most 254 characters |
| `phone` | optional, 7 to 20 characters of digits, spaces, `+`, `(`, `)` and `-` |
| `password` | required, 8 to 72 characters and at most 72 bytes in UTF-8 (the BCrypt limit), at least one letter and one digit |

Response: `201 Created`

```json
{
  "id": 42,
  "firstName": "Ali",
  "lastName": "Yılmaz",
  "email": "ali@ornek.test",
  "phone": "0532 000 00 00",
  "roles": ["MUSTERI"]
}
```

Errors: `EOD-400` with field errors; `EOD-009` (409) when an account with the email address exists.

### POST /auth/login

Status: implemented. Role: public (CSRF token required).

```json
{
  "email": "ali@ornek.test",
  "password": "parola123"
}
```

On success the server changes the session id (session fixation protection), stores the user id in the session and
issues a new CSRF token cookie. Response: `200 OK` with the same body as `GET /me`.

Errors: `EOD-010` (401) when the email address or the password is wrong, with the same detail in both cases;
`EOD-011` (403) when the password is correct but the account is inactive.

### POST /auth/logout

Status: implemented. Role: public (CSRF token required).

Invalidates the session and clears the CSRF token cookie. Response: `204 No Content`, also when no user was signed in.

## Account

### GET /me

Status: implemented. Role: signed in.

Response: `200 OK`. `phone` is `null` when not given; `roles` is sorted alphabetically.

```json
{
  "id": 42,
  "firstName": "Ali",
  "lastName": "Yılmaz",
  "email": "ali@ornek.test",
  "phone": "0532 000 00 00",
  "roles": ["MUSTERI"]
}
```

### PUT /me

Status: implemented. Role: signed in.

Replaces the name and phone number; the same rules as for registration apply. Omitting `phone` or sending `null`
removes the phone number. The email address cannot be changed.

```json
{
  "firstName": "Ali",
  "lastName": "Yıldız",
  "phone": null
}
```

Response: `200 OK` with the same body as `GET /me`.

### PUT /me/password

Status: implemented. Role: signed in.

```json
{
  "currentPassword": "parola123",
  "newPassword": "yeniParola456"
}
```

`newPassword` follows the registration password rules. Response: `204 No Content`. The session stays signed in.

Errors: `EOD-400` with field errors; `EOD-012` (400) when `currentPassword` is wrong.

## Listings

### GET /listings

Status: planned. Role: signed in.

Lists active listings (`PAKET_ILANI.durum = AKTIF`) with remaining stock whose pickup window has not ended, of approved
and active businesses, ordered by pickup start. Paginated.

| Query parameter | Meaning |
| --- | --- |
| `city`, `district` | exact match on the business's city and district |
| `type` | business type: `FIRIN`, `KAFE` or `RESTORAN` |
| `minPrice`, `maxPrice` | range of the sale price, both inclusive; `minPrice` must not exceed `maxPrice` |

Response: `200 OK`

```json
{
  "items": [
    {
      "id": 7,
      "businessId": 3,
      "businessName": "Ertaş Kafe",
      "businessType": "KAFE",
      "city": "Ankara",
      "district": "Keçiören",
      "title": "Akşam tatlı paketi",
      "salePrice": "60.00",
      "normalPrice": "150.00",
      "remainingQuantity": 4,
      "pickupStart": "2026-10-14T19:00:00",
      "pickupEnd": "2026-10-14T21:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

### GET /listings/{id}

Status: planned. Role: signed in.

Response: `200 OK` with the fields of a list item plus `description`, `allergenInfo`, `totalQuantity`, `status`,
`createdAt` and the business's `address` and `phone`. Inactive listings are returned only to the owner of the business
and to administrators; for everyone else they answer `EOD-404`.

## Orders

### POST /orders

Status: planned. Role: MUSTERI.

Places an order with one business and charges it in one transaction. Stock is reserved under row locks, the unit price
is the listing's current sale price, and the charge is recorded in `ODEME_ISLEMI` with `requestId` as `istek_no`.
Sending the same `requestId` again returns the order created by the first request instead of creating a second one.
`simulatePaymentFailure` (default `false`) makes the simulated payment provider decline the charge.

```json
{
  "businessId": 3,
  "items": [
    { "listingId": 7, "quantity": 2 }
  ],
  "requestId": "5f0c3a52-8a8e-4d55-9a53-3c1f1b7a2d10",
  "simulatePaymentFailure": false
}
```

| Field | Rule |
| --- | --- |
| `businessId` | required |
| `items` | 1 or more entries, each `listingId` at most once, `quantity` at least 1 |
| `requestId` | required, 1 to 64 characters, unique per request |

Response: `201 Created` with the body of `GET /orders/{id}`.

Errors: `EOD-001` (409) when a listing does not belong to the business; listing inactive, outside its pickup window or
out of stock (409, *new code*); payment declined (402, *new code*).

### GET /orders

Status: planned. Role: MUSTERI.

Lists the signed-in customer's orders, newest first. Optional query parameter `status`. Paginated; the items have the
fields of `GET /orders/{id}` without `items`.

### GET /orders/{id}

Status: planned. Role: MUSTERI (only the customer's own orders; others answer `EOD-404`).

Response: `200 OK`

```json
{
  "id": 120,
  "businessId": 3,
  "businessName": "Ertaş Kafe",
  "status": "ONAYLANDI",
  "createdAt": "2026-10-14T17:42:10",
  "cancelledAt": null,
  "items": [
    { "listingId": 7, "title": "Akşam tatlı paketi", "quantity": 2, "unitPrice": "60.00", "lineTotal": "120.00" }
  ],
  "totalAmount": "120.00",
  "refundedAmount": "0.00",
  "pickup": {
    "code": "482913",
    "windowStart": "2026-10-14T19:00:00",
    "windowEnd": "2026-10-14T21:00:00",
    "pickedUpAt": null
  },
  "review": null
}
```

### POST /orders/{id}/cancel

Status: planned. Role: MUSTERI (own orders).

Cancels a confirmed order before its pickup window starts, returns the stock and refunds the charge with `requestId` as
the refund's `istek_no`. Repeating the request with the same `requestId` returns the cancelled order.

```json
{
  "requestId": "0d7f3a10-2b55-4c1e-a0f4-7f4b8de2c3a9"
}
```

Response: `200 OK` with the body of `GET /orders/{id}`.

Errors: `EOD-005` (409) when the order is not `ONAYLANDI`; `EOD-004` (409) when the refund would exceed the charge;
cancellation period over (409, *new code*).

### POST /orders/{id}/review

Status: planned. Role: MUSTERI (own orders).

```json
{
  "rating": 5,
  "comment": "Çok taze ve lezzetliydi."
}
```

`rating` is required, 1 to 5; `comment` is optional, at most 1000 characters. Response: `201 Created`

```json
{
  "orderId": 120,
  "rating": 5,
  "comment": "Çok taze ve lezzetliydi.",
  "createdAt": "2026-10-14T21:30:00"
}
```

Errors: `EOD-006` (409) when the order is not `TESLIM_EDILDI`; the order already has a review (409, *new code*).

## Businesses

### POST /businesses

Status: planned. Role: MUSTERI.

Applies for a business. The signed-in user becomes its owner (`yetkili_id`); the application starts as `BEKLEMEDE`.

```json
{
  "name": "Ertaş Kafe",
  "type": "KAFE",
  "city": "Ankara",
  "district": "Keçiören",
  "address": "Kızlarpınarı Cad. No: 7",
  "phone": "0312 555 20 01"
}
```

Response: `201 Created`

```json
{
  "id": 3,
  "name": "Ertaş Kafe",
  "type": "KAFE",
  "city": "Ankara",
  "district": "Keçiören",
  "address": "Kızlarpınarı Cad. No: 7",
  "phone": "0312 555 20 01",
  "approvalStatus": "BEKLEMEDE",
  "active": true
}
```

### GET /businesses/mine

Status: planned. Role: MUSTERI.

Lists the businesses owned by the signed-in user in every approval state, so that an applicant can follow a pending
application before holding the `ISLETME_YETKILISI` role. Paginated; items have the body of `POST /businesses`.

### PUT /businesses/{id}

Status: planned. Role: owner of the business.

Replaces `name`, `city`, `district`, `address` and `phone` (same body as `POST /businesses` without `type`). The type,
approval status and active flag are not changed here. Response: `200 OK` with the business.

### POST /businesses/{id}/listings

Status: planned. Role: owner of the business.

```json
{
  "title": "Akşam tatlı paketi",
  "description": "Günün tatlılarından karışık 4 parça.",
  "allergenInfo": "Gluten, süt, yumurta içerir.",
  "normalPrice": "150.00",
  "salePrice": "60.00",
  "totalQuantity": 5,
  "pickupStart": "2026-10-14T19:00:00",
  "pickupEnd": "2026-10-14T21:00:00"
}
```

`salePrice` must be greater than zero and lower than `normalPrice`; `pickupStart` must be before `pickupEnd` and
`pickupEnd` in the future. The listing starts as `AKTIF` with `remainingQuantity` equal to `totalQuantity`. Response:
`201 Created` with the body of `GET /listings/{id}`.

Errors: `EOD-007` (409) when the business is not approved and active.

### PUT /businesses/{id}/listings/{listingId}/price

Status: planned. Role: owner of the business.

```json
{
  "salePrice": "55.00"
}
```

Changes the sale price for future orders; existing order items keep their unit price. Response: `200 OK` with the
listing.

### POST /businesses/{id}/listings/{listingId}/deactivate

Status: planned. Role: owner of the business.

Sets the listing to `PASIF`; confirmed orders are not affected. Repeating the request has no further effect. Response:
`200 OK` with the listing.

### GET /businesses/{id}/orders

Status: planned. Role: owner of the business.

Lists the business's orders whose pickup window starts on `date` (query parameter, default today), ordered by pickup
start. Optional `status`. Paginated; items have the fields of `GET /orders/{id}` plus `customerFirstName` and the
customer's initial `customerLastNameInitial`. The pickup code is not included.

### POST /businesses/{id}/pickups/verify

Status: planned. Role: owner of the business.

```json
{
  "code": "482913"
}
```

Marks the order with this pickup code as delivered (`TESLIM_EDILDI`) and records the signed-in user as the verifier.
Response: `200 OK` with the order.

Errors: `EOD-008` (403) when the user does not own the order's business; no confirmed order of this business with the
code (404, *new code*); outside the pickup window (409, *new code*).

### GET /businesses/{id}/reports

Status: planned. Role: owner of the business.

Sales summary of the business for the date range `from`–`to` (both required). Response: `200 OK`

```json
{
  "businessId": 3,
  "from": "2026-10-01",
  "to": "2026-10-31",
  "orderCount": 24,
  "deliveredCount": 20,
  "cancelledCount": 2,
  "notPickedUpCount": 2,
  "unitsSold": 31,
  "grossRevenue": "1860.00",
  "refundedAmount": "120.00",
  "netRevenue": "1740.00",
  "averageRating": "4.60"
}
```

`averageRating` is `null` when there are no reviews.

## Administration

### GET /admin/business-applications

Status: planned. Role: YONETICI.

Lists business applications, oldest first. Optional `status` (default `BEKLEMEDE`). Paginated; items have the body of
`POST /businesses` plus `ownerId`, `ownerFirstName` and `ownerLastName`.

### POST /admin/businesses/{id}/approve

Status: planned. Role: YONETICI.

Sets the application to `ONAYLANDI` and grants the owner the `ISLETME_YETKILISI` role in the same transaction. No
body. Response: `200 OK` with the business. Errors: application not `BEKLEMEDE` (409, *new code*).

### POST /admin/businesses/{id}/reject

Status: planned. Role: YONETICI.

Sets the application to `REDDEDILDI`. No body. Response: `200 OK` with the business. Errors: application not
`BEKLEMEDE` (409, *new code*).

### PUT /admin/users/{id}/active

Status: planned. Role: YONETICI.

```json
{
  "active": false
}
```

Activates or deactivates an account. A deactivated user's open session is rejected on the next request. Response:
`204 No Content`. Errors: an administrator deactivating their own account (409, *new code*).

### PUT /admin/businesses/{id}/active

Status: planned. Role: YONETICI.

Same body as `PUT /admin/users/{id}/active`. An inactive business's listings no longer appear in `GET /listings`.
Response: `200 OK` with the business.

### DELETE /admin/users/{id}/roles/{roleName}

Status: planned. Role: YONETICI.

Revokes a role (`MUSTERI`, `ISLETME_YETKILISI` or `YONETICI`); the user loses it on the next request. Revoking a role the
user does not hold has no effect. Response: `204 No Content`. Errors: unknown role name (`EOD-404`); an administrator
revoking their own `YONETICI` role (409, *new code*).

### POST /admin/listings/{id}/deactivate

Status: planned. Role: YONETICI.

Sets any listing to `PASIF`, for example when it breaks the platform rules. Response: `200 OK` with the listing.

### GET /admin/reports/platform

Status: planned. Role: YONETICI.

Platform-wide summary for the date range `from`–`to` (both required), read from the reporting views. Response:
`200 OK` with the fields of `GET /businesses/{id}/reports` without `businessId`, plus `activeBusinessCount` and
`newUserCount`.
