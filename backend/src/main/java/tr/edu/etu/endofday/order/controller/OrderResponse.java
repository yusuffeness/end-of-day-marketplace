package tr.edu.etu.endofday.order.controller;

import java.time.LocalDateTime;
import java.util.List;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.order.OrderLine;
import tr.edu.etu.endofday.order.OrderResult;
import tr.edu.etu.endofday.order.OrderView;

/**
 * The body of GET /orders/{id}. In list responses {@code items} is {@code null}. {@code canCancel} and
 * {@code canReview} tell the client which actions the rules allow at the time of the response.
 */
public record OrderResponse(
        long id,
        long businessId,
        String businessName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        List<Item> items,
        String totalAmount,
        String refundedAmount,
        Pickup pickup,
        Review review,
        boolean canCancel,
        boolean canReview) {

    public static OrderResponse from(OrderResult result) {
        return of(result, result.order().items().stream().map(Item::from).toList());
    }

    public static OrderResponse withoutItems(OrderResult result) {
        return of(result, null);
    }

    private static OrderResponse of(OrderResult result, List<Item> items) {
        OrderView order = result.order();
        return new OrderResponse(
                order.id(),
                order.businessId(),
                order.businessName(),
                order.status().name(),
                order.createdAt(),
                order.cancelledAt(),
                items,
                Money.format(order.totalAmount()),
                Money.format(order.refundedAmount()),
                order.pickup() == null ? null : Pickup.from(order.pickup()),
                order.review() == null ? null : Review.from(order.review()),
                result.cancellable(),
                result.reviewable());
    }

    @Override
    public String toString() {
        return "OrderResponse[id=" + id + ", status=" + status + "]";
    }

    /** An item with its line total (quantity times unit price). */
    public record Item(long listingId, String title, int quantity, String unitPrice, String lineTotal) {

        static Item from(OrderLine line) {
            return new Item(
                    line.listingId(),
                    line.title(),
                    line.quantity(),
                    Money.format(line.unitPrice()),
                    Money.format(line.lineTotal()));
        }
    }

    /** The pickup code and window of the order. */
    public record Pickup(String code, LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime pickedUpAt) {

        static Pickup from(OrderView.Pickup pickup) {
            return new Pickup(pickup.code(), pickup.windowStart(), pickup.windowEnd(), pickup.pickedUpAt());
        }

        @Override
        public String toString() {
            return "Pickup[windowStart=" + windowStart + ", windowEnd=" + windowEnd + "]";
        }
    }

    /** The review of the order. */
    public record Review(int rating, String comment, LocalDateTime createdAt) {

        static Review from(OrderView.Review review) {
            return new Review(review.rating(), review.comment(), review.createdAt());
        }
    }
}
