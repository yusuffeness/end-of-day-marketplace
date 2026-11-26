package tr.edu.etu.endofday.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import tr.edu.etu.endofday.payment.PaymentStatus;
import tr.edu.etu.endofday.payment.PaymentTransaction;
import tr.edu.etu.endofday.payment.PaymentType;
import tr.edu.etu.endofday.payment.repository.PaymentRepository;

/**
 * The simulated payment provider of the demo (report section 1.3): no card data is processed, and the client decides
 * through a flag of the request whether the provider accepts or declines. Charges and refunds are always for the full
 * amount (report section 4.3).
 *
 * <p>The methods run inside the caller's transaction.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Charges the order. A declined charge is not recorded: the caller rolls back the whole order, so that no table
     * keeps a trace of it.
     *
     * @return {@code true} when the provider accepted the charge and it was recorded as successful
     */
    public boolean charge(
            long orderId, String requestId, BigDecimal amount, boolean simulateFailure, LocalDateTime at) {
        if (simulateFailure) {
            return false;
        }
        paymentRepository.insert(orderId, null, requestId, PaymentType.TAHSILAT, amount, PaymentStatus.BASARILI, at);
        return true;
    }

    /**
     * Refunds the full charge. The refund is recorded in both outcomes: a declined refund stays in ODEME_ISLEMI as
     * BASARISIZ.
     *
     * @return {@code true} when the refund was successful
     */
    public boolean refundInFull(
            PaymentTransaction charge, String requestId, boolean simulateFailure, LocalDateTime at) {
        PaymentStatus status = simulateFailure ? PaymentStatus.BASARISIZ : PaymentStatus.BASARILI;
        paymentRepository.insert(
                charge.orderId(), charge.id(), requestId, PaymentType.IADE, charge.amount(), status, at);
        return !simulateFailure;
    }
}
