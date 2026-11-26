package tr.edu.etu.endofday.business.controller;

import java.time.LocalDateTime;
import java.util.List;
import tr.edu.etu.endofday.business.BusinessOrder;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.order.OrderLine;

/**
 * An item of GET /businesses/{id}/orders. The customer is shown as "Ad S."; no email address, phone or pickup code is
 * included.
 */
public record BusinessOrderResponse(
        long id,
        String status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String customerName,
        List<Item> items,
        String totalAmount,
        String refundedAmount,
        Pickup pickup) {

    public static BusinessOrderResponse from(BusinessOrder order) {
        return new BusinessOrderResponse(
                order.id(),
                order.status().name(),
                order.createdAt(),
                order.cancelledAt(),
                order.customerShortName(),
                order.items().stream().map(Item::from).toList(),
                Money.format(order.totalAmount()),
                Money.format(order.refundedAmount()),
                new Pickup(order.pickupStart(), order.pickupEnd(), order.pickedUpAt()));
    }

    /** An item with its line total. */
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

    /** The pickup window and time, without the code. */
    public record Pickup(LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime pickedUpAt) {}
}
