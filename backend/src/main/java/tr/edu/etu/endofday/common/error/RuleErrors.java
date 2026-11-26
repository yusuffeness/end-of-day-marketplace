package tr.edu.etu.endofday.common.error;

import org.springframework.http.HttpStatus;

/**
 * The business rule errors raised by the services (docs/error-codes.md). Codes up to EOD-008 are shared with the
 * database triggers: the services check those rules first so that the client gets a precise answer, and the triggers
 * stay the final guarantee.
 */
public final class RuleErrors {

    private RuleErrors() {}

    /** EOD-001: a listing of the order belongs to another business. */
    public static ApiException listingOfAnotherBusiness() {
        return conflict("EOD-001", "Sipariş kalemindeki ilan, siparişin işletmesine ait değil.");
    }

    /** EOD-005: only a confirmed order can be cancelled. */
    public static ApiException orderNotCancellable() {
        return conflict("EOD-005", "Yalnızca onaylanmış bir sipariş iptal edilebilir.");
    }

    /** EOD-006: only a delivered order can be reviewed. */
    public static ApiException orderNotDelivered() {
        return conflict("EOD-006", "Yalnızca teslim edilmiş sipariş değerlendirilebilir.");
    }

    /** EOD-007: listings are published only for an approved and active business. */
    public static ApiException businessNotOpenForListings() {
        return conflict("EOD-007", "İlan yalnızca onaylı ve aktif bir işletme için yayına alınabilir.");
    }

    /** EOD-008: only the owner of the order's business verifies the pickup. */
    public static ApiException notOwnerOfBusiness() {
        return new ApiException(
                HttpStatus.FORBIDDEN, "EOD-008", "Teslimi yalnızca siparişin işletmesinin yetkilisi doğrulayabilir.");
    }

    /** EOD-013: the listing is inactive or its business is not approved and active. */
    public static ApiException listingNotAvailable() {
        return conflict("EOD-013", "İlan şu anda satışta değil.");
    }

    /** EOD-014: fewer units are left than ordered. */
    public static ApiException insufficientStock() {
        return conflict("EOD-014", "İlanda yeterli adet kalmadı.");
    }

    /** EOD-015: the pickup windows of the listings have no common time. */
    public static ApiException noCommonPickupWindow() {
        return conflict("EOD-015", "Seçilen ilanların ortak bir teslim alma aralığı yok.");
    }

    /** EOD-016: the common pickup window has already started. */
    public static ApiException pickupWindowStarted() {
        return conflict("EOD-016", "Teslim alma aralığı başladığı için sipariş verilemez.");
    }

    /** EOD-017: the simulated payment provider declined the charge. */
    public static ApiException paymentDeclined() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "EOD-017", "Ödeme reddedildi, sipariş oluşturulmadı.");
    }

    /** EOD-018: the request id belongs to a different request. */
    public static ApiException requestIdInUse() {
        return conflict("EOD-018", "Bu istek numarası başka bir işlem için kullanılmış.");
    }

    /** EOD-019: the pickup window has started, so the customer can no longer cancel. */
    public static ApiException cancellationPeriodOver() {
        return conflict("EOD-019", "Teslim alma aralığı başladığı için sipariş iptal edilemez.");
    }

    /** EOD-020: the simulated payment provider declined the refund; the order stays confirmed. */
    public static ApiException refundDeclined() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "EOD-020", "İade reddedildi, sipariş iptal edilmedi.");
    }

    /** EOD-021: no confirmed order of the business has this pickup code. */
    public static ApiException pickupCodeNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "EOD-021", "Bu işletmede bu koda ait onaylanmış bir sipariş yok.");
    }

    /** EOD-022: the pickup is verified outside the pickup window. */
    public static ApiException outsidePickupWindow() {
        return conflict("EOD-022", "Teslim yalnızca teslim alma aralığında yapılabilir.");
    }

    /** EOD-023: the pickup code was already used. */
    public static ApiException pickupCodeUsed() {
        return conflict("EOD-023", "Bu teslim kodu daha önce kullanıldı.");
    }

    /** EOD-024: the order already has a review. */
    public static ApiException alreadyReviewed() {
        return conflict("EOD-024", "Bu sipariş zaten değerlendirildi.");
    }

    /** EOD-025: the pickup window of a new listing does not start before it ends or does not end in the future. */
    public static ApiException invalidPickupWindow() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "EOD-025",
                "Teslim alma aralığının başlangıcı bitişinden önce olmalı ve bitişi gelecekte olmalıdır.");
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }
}
