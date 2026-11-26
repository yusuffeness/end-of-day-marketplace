package tr.edu.etu.endofday.business;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import tr.edu.etu.endofday.order.OrderLine;
import tr.edu.etu.endofday.order.OrderStatus;

/**
 * An order as the business sees it. The customer appears only as first name and last-name initial, and the pickup code
 * is not part of it: the customer tells the code and the business verifies it.
 */
public record BusinessOrder(
        long id,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String customerFirstName,
        String customerLastName,
        BigDecimal totalAmount,
        BigDecimal refundedAmount,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        LocalDateTime pickedUpAt,
        List<OrderLine> items) {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    public BusinessOrder {
        items = List.copyOf(items);
    }

    public BusinessOrder withItems(List<OrderLine> lines) {
        return new BusinessOrder(
                id,
                status,
                createdAt,
                cancelledAt,
                customerFirstName,
                customerLastName,
                totalAmount,
                refundedAmount,
                pickupStart,
                pickupEnd,
                pickedUpAt,
                lines);
    }

    /** "Ad S.": the first name and the upper-case initial of the last name, with Turkish casing (i becomes İ). */
    public String customerShortName() {
        String lastName = customerLastName.strip();
        if (lastName.isEmpty()) {
            return customerFirstName;
        }
        String initial = new String(Character.toChars(lastName.codePointAt(0))).toUpperCase(TURKISH);
        return customerFirstName + " " + initial + ".";
    }

    @Override
    public String toString() {
        return "BusinessOrder[id=" + id + ", status=" + status + "]";
    }
}
