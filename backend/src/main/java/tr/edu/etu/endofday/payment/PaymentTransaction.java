package tr.edu.etu.endofday.payment;

import java.math.BigDecimal;

/** A row of ODEME_ISLEMI. {@code sourceId} is the charge a refund reverses and {@code null} for a charge. */
public record PaymentTransaction(
        long id,
        long orderId,
        Long sourceId,
        String requestId,
        PaymentType type,
        BigDecimal amount,
        PaymentStatus status) {

    public boolean isSuccessful() {
        return status == PaymentStatus.BASARILI;
    }
}
