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
| GET | `/listings` | signed in | implemented |
| GET | `/listings/{id}` | signed in | implemented |
| POST | `/orders` | MUSTERI | implemented |
| GET | `/orders` | MUSTERI | implemented |
| GET | `/orders/{id}` | MUSTERI | implemented |
| POST | `/orders/{id}/cancel` | MUSTERI | implemented |
| POST | `/orders/{id}/review` | MUSTERI | implemented |
| POST | `/businesses` | signed in | implemented |
| GET | `/businesses/mine` | signed in | implemented |
| PUT | `/businesses/{id}` | owner of the business | implemented |
| GET | `/businesses/{id}/listings` | owner of the business | implemented |
| POST | `/businesses/{id}/listings` | owner of the business | implemented |
| PUT | `/businesses/{id}/listings/{listingId}/price` | owner of the business | implemented |
| POST | `/businesses/{id}/listings/{listingId}/deactivate` | owner of the business | implemented |
| GET | `/businesses/{id}/orders` | owner of the business | implemented |
| POST | `/businesses/{id}/pickups/verify` | owner of the business | implemented |
| GET | `/businesses/{id}/reports` | owner of the business | implemented |
| GET | `/admin/business-applications` | YONETICI | implemented |
| POST | `/admin/businesses/{id}/approve` | YONETICI | implemented |
| POST | `/admin/businesses/{id}/reject` | YONETICI | implemented |
| GET | `/admin/users` | YONETICI | implemented |
| PUT | `/admin/users/{id}/active` | YONETICI | implemented |
| PUT | `/admin/businesses/{id}/active` | YONETICI | implemented |
| PUT | `/admin/users/{id}/roles/{roleName}` | YONETICI | implemented |
| DELETE | `/admin/users/{id}/roles/{roleName}` | YONETICI | implemented |
| GET | `/admin/listings` | YONETICI | implemented |
| POST | `/admin/listings/{id}/deactivate` | YONETICI | implemented |
| GET | `/admin/reports/platform` | YONETICI | implemented |

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

Status: implemented. Role: signed in.

Lists only the listings that can be bought now: active (`PAKET_ILANI.durum = AKTIF`), with remaining stock, of an
approved and active business, and with a pickup window that has not started (an order is accepted only before the
window starts). Ordered by pickup start, then by id. Paginated.

| Query parameter | Meaning |
| --- | --- |
| `city`, `district` | match on the business's city and district (Turkish collation, ignoring letter case) |
| `type` | business type: `FIRIN`, `KAFE` or `RESTORAN` |
| `minPrice`, `maxPrice` | range of the sale price, both inclusive; `minPrice` must not exceed `maxPrice` |

Errors: `EOD-400` for an unknown `type`, an invalid amount, `minPrice` greater than `maxPrice` or a paging value out of
bounds.

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

Status: implemented. Role: signed in.

Response: `200 OK` with the fields of a list item plus `description`, `allergenInfo`, `totalQuantity`, `status`,
`createdAt` and the business's `address` and `phone`.

```json
{
  "id": 7,
  "businessId": 3,
  "businessName": "Ertaş Kafe",
  "businessType": "KAFE",
  "city": "Ankara",
  "district": "Keçiören",
  "address": "Kızlarpınarı Cad. No: 7",
  "phone": "0312 555 20 01",
  "title": "Akşam tatlı paketi",
  "description": "Günün tatlılarından karışık 4 parça.",
  "allergenInfo": "Gluten, süt, yumurta içerir.",
  "salePrice": "60.00",
  "normalPrice": "150.00",
  "totalQuantity": 5,
  "remainingQuantity": 4,
  "pickupStart": "2026-10-14T19:00:00",
  "pickupEnd": "2026-10-14T21:00:00",
  "status": "AKTIF",
  "createdAt": "2026-10-14T12:00:00"
}
```

A listing that is inactive, or whose business is not approved and active, is returned only to the owner of the
business and to administrators; for everyone else it answers `EOD-404`. Sold-out listings and listings whose pickup
window has started stay readable, so that customers can look up the listings of their orders.

## Orders

### POST /orders

Status: implemented. Role: MUSTERI.

Places an order with one business and charges it in one transaction (report section 7.2): the business row is read
with a shared lock and the listing rows with exclusive locks in ascending id order, then the order and its items are
inserted, the stock is decreased, the successful charge is recorded in `ODEME_ISLEMI` with `requestId` as `istek_no`
and the pickup record with a new six-digit code is inserted. The unit price is the listing's sale price read from the
database; prices sent by the client are ignored.

Sending the same `requestId` again returns the order created by the first request (`201 Created`, same body) instead of
creating a second one, also when the two requests run at the same time. `simulatePaymentFailure` (default `false`)
makes the simulated payment provider decline the charge: the transaction is rolled back and no table keeps a trace of
the order, so the same `requestId` can be sent again.

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
| `items` | 1 to 50 entries, `quantity` 1 to 100; entries with the same `listingId` are merged into one item and their quantities added up (report section 4.2) |
| `requestId` | required, 1 to 64 characters, unique per request |

The pickup window of the order is the intersection of the pickup windows of its listings and is fixed in
`TESLIM_ALMA` when the order is created. The order is accepted only when this intersection is not empty and has not
started yet.

Response: `201 Created` with the body of `GET /orders/{id}`.

Errors: `EOD-404` for an unknown business or listing; `EOD-013` (409) when the business is not approved and active or
a listing is inactive; `EOD-001` (409) when a listing belongs to another business; `EOD-014` (409) when a listing has
fewer units left than ordered; `EOD-015` (409) when the pickup windows have no common time; `EOD-016` (409) when the
common window has started; `EOD-017` (402) when the payment is declined; `EOD-018` (409) when the `requestId` was used
by another customer or for a refund.

### GET /orders

Status: implemented. Role: MUSTERI.

Lists the signed-in customer's orders, newest first. Optional query parameter `status` (`ONAYLANDI`, `TESLIM_EDILDI`,
`IPTAL_EDILDI` or `TESLIM_ALINMADI`). Paginated; the items have the fields of `GET /orders/{id}` with `items` set to
`null`.

### GET /orders/{id}

Status: implemented. Role: MUSTERI (only the customer's own orders; others answer `EOD-404`).

`totalAmount` is the sum of quantity times unit price of the items and `refundedAmount` the sum of the successful
refunds. `canCancel` is `true` while the order is `ONAYLANDI` and its pickup window has not started; `canReview` is
`true` when the order is `TESLIM_EDILDI` and has no review yet. Clients use these flags instead of evaluating the rules
themselves.

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
  "review": null,
  "canCancel": true,
  "canReview": false
}
```

### POST /orders/{id}/cancel

Status: implemented. Role: MUSTERI (own orders).

Cancels a confirmed order before its pickup window starts, in one transaction: the order row is locked, the full
charge is refunded with `requestId` as the refund's `istek_no`, the stock is returned once and the status becomes
`IPTAL_EDILDI` with `cancelledAt` set. `simulateRefundFailure` (default `false`) makes the simulated payment provider
decline the refund: the refund is stored as `BASARISIZ`, the order stays `ONAYLANDI`, the stock is not touched and the
answer is `EOD-020`.

Repeating the request with the same `requestId` returns the result of the first request: the cancelled order, or
`EOD-020` again when that refund was declined. A new attempt after a declined refund needs a new `requestId`.

```json
{
  "requestId": "0d7f3a10-2b55-4c1e-a0f4-7f4b8de2c3a9",
  "simulateRefundFailure": false
}
```

Response: `200 OK` with the body of `GET /orders/{id}`.

Errors: `EOD-005` (409) when the order is not `ONAYLANDI`; `EOD-019` (409) when its pickup window has started;
`EOD-020` (402) when the refund is declined; `EOD-018` (409) when the `requestId` was used for another order or for a
charge.

### POST /orders/{id}/review

Status: implemented. Role: MUSTERI (own orders).

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

An empty comment is stored as `null`.

Errors: `EOD-006` (409) when the order is not `TESLIM_EDILDI`; `EOD-024` (409) when the order already has a review.

## Businesses

"Owner of the business" means the signed-in user is `ISLETME.yetkili_id` of the business in the path and holds the
`ISLETME_YETKILISI` role. For every endpoint below with that role, an unknown business answers `EOD-404` and a business
of another owner answers `EOD-403` (`EOD-008` for the pickup verification); a listing id of another business answers
`EOD-404`.

### POST /businesses

Status: implemented. Role: signed in.

Applies for a business. The signed-in user becomes its owner (`yetkili_id`); the application starts as `BEKLEMEDE`
and active. The `ISLETME_YETKILISI` role is granted only when an administrator approves it.

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

| Field | Rule |
| --- | --- |
| `name` | required, at most 100 characters |
| `type` | required: `FIRIN`, `KAFE` or `RESTORAN` |
| `city`, `district` | required, at most 50 characters |
| `address` | required, at most 255 characters |
| `phone` | required, 7 to 20 characters of digits, spaces, `+`, `(`, `)` and `-` |

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

Errors: `EOD-400` with field errors.

### GET /businesses/mine

Status: implemented. Role: signed in.

Lists the businesses owned by the signed-in user in every approval state, newest first, so that an applicant can
follow a pending application before holding the `ISLETME_YETKILISI` role. Paginated; items have the body of
`POST /businesses`.

### PUT /businesses/{id}

Status: implemented. Role: owner of the business.

Replaces `name`, `city`, `district`, `address` and `phone` with the rules of `POST /businesses`; `type` is ignored. The
type, approval status and active flag are not changed here. Response: `200 OK` with the business.

### GET /businesses/{id}/listings

Status: implemented. Role: owner of the business.

Lists every listing of the business, active and inactive, newest first. Optional `status` (`AKTIF` or `PASIF`).
Paginated; items have the body of `GET /listings/{id}`.

### POST /businesses/{id}/listings

Status: implemented. Role: owner of the business.

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

`title` (at most 100 characters), `description` (at most 2000) and `allergenInfo` (at most 500; when there are no
allergens the business says so) are required. Amounts have up to 8 integer digits and up to 2 decimals. `salePrice`
must be greater than zero and lower than `normalPrice`; `totalQuantity` is 1 to 10000. The pickup window must start in
the future, before it ends, and end on the same day (report section 1.3: every listing belongs to one day; a window
that ends at midnight ends on the next day). Times are stored in whole seconds. The listing starts as `AKTIF` with
`remainingQuantity` equal to `totalQuantity`. Response: `201 Created` with the body of `GET /listings/{id}`.

Errors: `EOD-400` with field errors; `EOD-007` (409) when the business is not approved and active; `EOD-025` (400) for
an invalid pickup window.

### PUT /businesses/{id}/listings/{listingId}/price

Status: implemented. Role: owner of the business.

```json
{
  "salePrice": "55.00"
}
```

Changes the sale price for future orders. The listing row is locked, so a concurrent order is charged either the old or
the new price; existing order items keep their unit price. Response: `200 OK` with the body of `GET /listings/{id}`.

Errors: `EOD-400` for a malformed amount; `EOD-029` (400) when the price is not greater than zero and lower than the
normal price.

### POST /businesses/{id}/listings/{listingId}/deactivate

Status: implemented. Role: owner of the business.

Sets the listing to `PASIF`: it leaves the listing search and takes no new orders. Confirmed orders are not affected.
Repeating the request has no further effect. No body. Response: `200 OK` with the body of `GET /listings/{id}`.

### GET /businesses/{id}/orders

Status: implemented. Role: owner of the business.

Lists the business's orders whose pickup window starts on `date` (query parameter, ISO date, default today), ordered
by pickup start. Optional `status`; `status=TESLIM_ALINMADI` lists the orders of the day that were not picked up.
Paginated.

The customer is shown as `customerName`: the first name and the upper-case initial of the last name ("Ahmet Y.", with
Turkish casing, so "ilhan" becomes "İ."). The email address, the phone number and the pickup code are never included:
the customer tells the code and the business verifies it with `POST /businesses/{id}/pickups/verify`.

```json
{
  "items": [
    {
      "id": 120,
      "status": "ONAYLANDI",
      "createdAt": "2026-10-14T17:42:10",
      "cancelledAt": null,
      "customerName": "Ahmet Y.",
      "items": [
        { "listingId": 7, "title": "Akşam tatlı paketi", "quantity": 2, "unitPrice": "60.00", "lineTotal": "120.00" }
      ],
      "totalAmount": "120.00",
      "refundedAmount": "0.00",
      "pickup": {
        "windowStart": "2026-10-14T19:00:00",
        "windowEnd": "2026-10-14T21:00:00",
        "pickedUpAt": null
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

### POST /businesses/{id}/pickups/verify

Status: implemented. Role: owner of the business.

```json
{
  "code": "482913"
}
```

Marks the order with this pickup code as delivered (`TESLIM_EDILDI`) and records the pickup time and the signed-in
user as the verifier together, in one transaction. The order row is locked and both updates are conditional, so a code
works only once. Both ends of the pickup window count as inside. Response: `200 OK` with the body of
`GET /orders/{id}`.

Errors: `EOD-400` when the code is not six digits; `EOD-008` (403) when the user does not own the business; `EOD-021`
(404) when no confirmed order of this business has the code; `EOD-023` (409) when the code was already used; `EOD-022`
(409) outside the pickup window.

### GET /businesses/{id}/reports

Status: implemented. Role: owner of the business.

Sales summary of the business for the date range `from`–`to` (both required, ISO dates, both included, at most 366
days). An order belongs to the range by its creation time. Response: `200 OK`

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
  "ratingCount": 15,
  "averageRating": "4.60",
  "ratingDistribution": { "1": 0, "2": 0, "3": 1, "4": 4, "5": 10 }
}
```

| Field | Meaning |
| --- | --- |
| `orderCount` and the three status counts | orders created in the range; confirmed orders are counted in `orderCount` only |
| `unitsSold` | item quantities of every order that was not cancelled; orders that were not picked up stay paid and are counted |
| `grossRevenue`, `refundedAmount`, `netRevenue` | successful charges, successful refunds and their difference |
| `ratingCount`, `averageRating`, `ratingDistribution` | reviews of the orders in the range; `averageRating` has two decimals and is `null` without reviews |

Errors: `EOD-400` when `from` or `to` is missing or malformed; `EOD-030` (400) when `from` is after `to` or the range
is longer than 366 days.

## Administration

Every endpoint below requires the `YONETICI` role; any other user gets `EOD-403`. Changes to accounts and roles take
effect on the user's next request, because roles and the active flag are read from the database on every request.

### GET /admin/business-applications

Status: implemented. Role: YONETICI.

Lists business applications, oldest first. Optional `status` (default `BEKLEMEDE`). Paginated; items have the body of
`POST /businesses` plus `ownerId`, `ownerFirstName` and `ownerLastName`.

### POST /admin/businesses/{id}/approve

Status: implemented. Role: YONETICI.

Sets the application to `ONAYLANDI` and grants the owner the `ISLETME_YETKILISI` role in the same transaction; the
business row is locked, so an application is decided only once. The owner can use the owner endpoints on the next
request without signing in again. No body. Response: `200 OK` with the business.

Errors: `EOD-404` for an unknown business; `EOD-026` (409) when the application is not `BEKLEMEDE`.

### POST /admin/businesses/{id}/reject

Status: implemented. Role: YONETICI.

Sets the application to `REDDEDILDI`. No body. Response: `200 OK` with the business.

Errors: `EOD-404` for an unknown business; `EOD-026` (409) when the application is not `BEKLEMEDE`.

### GET /admin/users

Status: implemented. Role: YONETICI.

Lists accounts ordered by id. Optional query parameters: `q` (part of the first name, last name or email address,
ignoring letter case; `%` and `_` match themselves), `role` (`MUSTERI`, `ISLETME_YETKILISI` or `YONETICI`) and
`active` (`true` or `false`). Paginated. Response: `200 OK`

```json
{
  "items": [
    {
      "id": 42,
      "firstName": "Ali",
      "lastName": "Yılmaz",
      "email": "ali@ornek.test",
      "phone": "0532 000 00 00",
      "active": true,
      "registeredAt": "2026-09-02T10:15:00",
      "roles": ["MUSTERI"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

Errors: unknown `role` value (`EOD-400`).

### PUT /admin/users/{id}/active

Status: implemented. Role: YONETICI.

```json
{
  "active": false
}
```

Activates or deactivates an account. A deactivated user's open session is rejected on the next request with
`EOD-401`. Response: `204 No Content`.

Errors: `EOD-404` for an unknown user; `EOD-027` (409) when administrators deactivate their own account.

### PUT /admin/businesses/{id}/active

Status: implemented. Role: YONETICI.

Same body as `PUT /admin/users/{id}/active`. An inactive business's listings no longer appear in `GET /listings` and
take no orders; they come back when the business is activated again. Response: `200 OK` with the business.

### PUT /admin/users/{id}/roles/{roleName}

Status: implemented. Role: YONETICI.

Grants a role (`MUSTERI`, `ISLETME_YETKILISI` or `YONETICI`); the user holds it from the next request on. Granting a
role the user already holds has no effect. No body. Response: `204 No Content`. `ISLETME_YETKILISI` is normally granted
by `POST /admin/businesses/{id}/approve`; granting it here gives no right over a business the user does not own.

Errors: unknown user or role name (`EOD-404`).

### DELETE /admin/users/{id}/roles/{roleName}

Status: implemented. Role: YONETICI.

Revokes a role by deleting the `KULLANICI_ROL` row, the only delete the application account is allowed to run; the
user loses the role on the next request. Revoking a role the user does not hold has no effect. Response:
`204 No Content`.

Errors: unknown user or role name (`EOD-404`); `EOD-028` (409) when administrators revoke their own `YONETICI` role.

### GET /admin/listings

Status: implemented. Role: YONETICI.

Lists every listing of every business, including inactive and expired ones, newest first, for inspection. Optional
query parameters: `businessId`, `status` (`AKTIF` or `PASIF`) and the ISO date range `from`–`to` on the pickup start
(both included). Paginated; items have the body of `GET /listings/{id}`.

Errors: `EOD-400` when `from` is after `to`.

### POST /admin/listings/{id}/deactivate

Status: implemented. Role: YONETICI.

Sets any listing to `PASIF`, for example when it breaks the platform rules. Repeating it has no further effect. No
body. Response: `200 OK` with the body of `GET /listings/{id}`.

Errors: `EOD-404` for an unknown listing.

### GET /admin/reports/platform

Status: implemented. Role: YONETICI.

Platform-wide summary for the date range `from`–`to`, with the rules of `GET /businesses/{id}/reports`. It is read
through the separate read-only database account `eod_report` from the reporting views only (`v_order_summary`,
`v_payment_summary`, `v_review_summary`, `v_business_status`, `v_user_registrations`); that account cannot read any
base table. Response: `200 OK` with the fields of `GET /businesses/{id}/reports` without `businessId`, plus:

| Field | Meaning |
| --- | --- |
| `activeBusinessCount` | approved and active businesses at the time of the request |
| `newUserCount` | accounts registered in the range |

Errors: as for `GET /businesses/{id}/reports`.
