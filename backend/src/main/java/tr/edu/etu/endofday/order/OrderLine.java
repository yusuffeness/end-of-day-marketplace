package tr.edu.etu.endofday.order;

import java.math.BigDecimal;

/** An item of an order with the title of its listing. */
public record OrderLine(long listingId, String title, int quantity, BigDecimal unitPrice) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
