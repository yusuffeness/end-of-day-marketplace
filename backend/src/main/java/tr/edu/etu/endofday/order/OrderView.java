package tr.edu.etu.endofday.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An order as the customer sees it. {@code totalAmount} is the sum of quantity times unit price of the items and
 * {@code refundedAmount} the sum of its successful refunds. {@code items} is empty in list views.
 */
public record OrderView(
        long id,
        long customerId,
        long businessId,
        String businessName,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        BigDecimal totalAmount,
        BigDecimal refundedAmount,
        Pickup pickup,
        Review review,
        List<OrderLine> items) {

    public OrderView {
        items = List.copyOf(items);
    }

    public OrderView withItems(List<OrderLine> lines) {
        return new OrderView(
                id,
                customerId,
                businessId,
                businessName,
                status,
                createdAt,
                cancelledAt,
                totalAmount,
                refundedAmount,
                pickup,
                review,
                lines);
    }

    /** The order can be cancelled while it is confirmed and its pickup window has not started. */
    public boolean isCancellableAt(LocalDateTime now) {
        return status == OrderStatus.ONAYLANDI && pickup != null && now.isBefore(pickup.windowStart());
    }

    /** A delivered order can be reviewed once. */
    public boolean isReviewable() {
        return status == OrderStatus.TESLIM_EDILDI && review == null;
    }

    /** The TESLIM_ALMA row of the order. */
    public record Pickup(String code, LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime pickedUpAt) {

        @Override
        public String toString() {
            return "Pickup[windowStart=" + windowStart + ", windowEnd=" + windowEnd + ", pickedUpAt=" + pickedUpAt
                    + "]";
        }
    }

    /** The DEGERLENDIRME row of the order. */
    public record Review(int rating, String comment, LocalDateTime createdAt) {}
}
