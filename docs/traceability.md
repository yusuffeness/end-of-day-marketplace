# Traceability

How the menus of the project report (section 6) map to the API endpoints of [api.md](api.md), and which integration
tests prove each endpoint. All paths are relative to `/api/v1`; all tests are in `backend/src/test/java` and run
against MySQL 8.4 with Testcontainers.

## Report menus → endpoints

| Report menu (section 6) | Functions | Endpoints |
| --- | --- | --- |
| Ortak İşlemler | sign up, sign in, sign out, profile and password | `GET /auth/csrf`, `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /me`, `PUT /me`, `PUT /me/password` |
| Müşteri Ana Sayfası | filter active listings by city, district, business type and price; only purchasable packages | `GET /listings` |
| Paket Ayrıntıları | description, allergens, normal and sale price, remaining units, business address, pickup window | `GET /listings/{id}` |
| Sepet ve Sipariş | packages of one business with quantities, total, order with the simulated payment | `POST /orders` (the cart lives in the client; the total is computed by the server from database prices) |
| Siparişlerim | past orders and their status, pickup code, cancellation and refund, review after delivery | `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`, `POST /orders/{id}/review` |
| İşletme Başvurusu | apply with the business details, follow the approval | `POST /businesses`, `GET /businesses/mine` |
| İşletme Yönetimi | update an owned business; open, deactivate and reprice daily listings | `PUT /businesses/{id}`, `GET /businesses/{id}/listings`, `POST /businesses/{id}/listings`, `PUT /businesses/{id}/listings/{listingId}/price`, `POST /businesses/{id}/listings/{listingId}/deactivate` |
| İşletme Siparişleri | own orders, verify the pickup code, complete the pickup, the day's orders that were not picked up | `GET /businesses/{id}/orders` (with `status=TESLIM_ALINMADI`), `POST /businesses/{id}/pickups/verify` |
| İşletme Raporları | units sold, cancellations, net collection and ratings for a date range | `GET /businesses/{id}/reports` |
| Yönetici Paneli | review applications; manage users, roles and business activity; inspect listings; platform reports | `GET /admin/business-applications`, `POST /admin/businesses/{id}/approve`, `POST /admin/businesses/{id}/reject`, `GET /admin/users`, `PUT /admin/users/{id}/active`, `PUT /admin/businesses/{id}/active`, `PUT /admin/users/{id}/roles/{roleName}`, `DELETE /admin/users/{id}/roles/{roleName}`, `GET /admin/listings`, `POST /admin/listings/{id}/deactivate`, `GET /admin/reports/platform` |

## Endpoints → tests

`AccessControlIntegrationTest` additionally checks every protected endpoint for anonymous callers (401), callers
without the required role (403) and owners of another business (403, `EOD-008` for the pickup verification), and the
order endpoints for another customer (404).

| Endpoint | Tests |
| --- | --- |
| `GET /auth/csrf` | `auth.AuthIntegrationTest` |
| `POST /auth/register` | `auth.AuthIntegrationTest`, `auth.RegistrationTransactionIntegrationTest` |
| `POST /auth/login`, `POST /auth/logout` | `auth.AuthIntegrationTest` |
| `GET /me`, `PUT /me`, `PUT /me/password` | `user.MeIntegrationTest`, `auth.AuthIntegrationTest`, `auth.RoleChangeIntegrationTest` |
| `GET /listings` | `listing.ListingIntegrationTest`, `business.BusinessManagementIntegrationTest`, `admin.AdminIntegrationTest` |
| `GET /listings/{id}` | `listing.ListingIntegrationTest`, `order.OrderPlacementIntegrationTest` |
| `POST /orders` | `order.OrderPlacementIntegrationTest`, `order.OrderConcurrencyIntegrationTest` |
| `GET /orders`, `GET /orders/{id}` | `order.OrderPlacementIntegrationTest`, `order.OrderCancellationIntegrationTest`, `review.ReviewIntegrationTest` |
| `POST /orders/{id}/cancel` | `order.OrderCancellationIntegrationTest` |
| `POST /orders/{id}/review` | `review.ReviewIntegrationTest`, `order.OrderPlacementIntegrationTest` (report example 6.1) |
| `POST /businesses`, `GET /businesses/mine` | `business.BusinessApplicationIntegrationTest` |
| `PUT /businesses/{id}` | `business.BusinessManagementIntegrationTest` |
| `GET /businesses/{id}/listings` | `business.BusinessManagementIntegrationTest`, `admin.AdminIntegrationTest` |
| `POST /businesses/{id}/listings` | `listing.ListingPublishingIntegrationTest`, `business.BusinessManagementIntegrationTest`, `order.OrderPlacementIntegrationTest` (report example 6.1) |
| `PUT /businesses/{id}/listings/{listingId}/price` | `business.BusinessManagementIntegrationTest` |
| `POST /businesses/{id}/listings/{listingId}/deactivate` | `business.BusinessManagementIntegrationTest` |
| `GET /businesses/{id}/orders` | `business.BusinessOrdersIntegrationTest` |
| `POST /businesses/{id}/pickups/verify` | `pickup.PickupIntegrationTest`, `order.OrderPlacementIntegrationTest` (report example 6.1) |
| `GET /businesses/{id}/reports` | `report.BusinessReportIntegrationTest`, `schema.ReportDemoDataIntegrationTest` (demo data, values computed by hand) |
| `GET /admin/business-applications`, `POST /admin/businesses/{id}/approve`, `POST /admin/businesses/{id}/reject` | `admin.AdminIntegrationTest`, `admin.ApprovalTransactionIntegrationTest` |
| `GET /admin/users`, `PUT /admin/users/{id}/active` | `admin.AdminIntegrationTest` |
| `PUT /admin/businesses/{id}/active` | `admin.AdminIntegrationTest` |
| `PUT /admin/users/{id}/roles/{roleName}`, `DELETE /admin/users/{id}/roles/{roleName}` | `admin.AdminIntegrationTest` |
| `GET /admin/listings`, `POST /admin/listings/{id}/deactivate` | `admin.AdminIntegrationTest` |
| `GET /admin/reports/platform` | `report.PlatformReportIntegrationTest`, `schema.ReportDemoDataIntegrationTest`, `schema.ReportViewsIntegrationTest`, `schema.PrivilegesIntegrationTest` |

## Transaction flows of report section 7.2

| Flow | Concurrency | Repeated request | Tests |
| --- | --- | --- | --- |
| Placing an order | 10 customers race for the last unit, exactly one wins, in 10 rounds; concurrent requests with one request id create one order | the same request id returns the first order | `order.OrderConcurrencyIntegrationTest`, `order.OrderPlacementIntegrationTest` |
| Cancellation | the order row is locked; the status change is conditional | the same request id creates no second refund or stock return; a declined refund is kept and answered again | `order.OrderCancellationIntegrationTest` |
| Pickup | the order row is locked; both updates are conditional | a second verification with the same code is rejected | `pickup.PickupIntegrationTest` |
