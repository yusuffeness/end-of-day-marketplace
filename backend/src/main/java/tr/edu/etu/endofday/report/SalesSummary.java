package tr.edu.etu.endofday.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The sales figures of a report range (report section 6, "İşletme Raporları").
 *
 * <ul>
 *   <li>{@code unitsSold} counts the item quantities of every order that was not cancelled; orders that were not picked
 *       up stay paid and are counted (report section 8.2).
 *   <li>{@code grossRevenue} is the sum of the successful charges and {@code refundedAmount} the sum of the successful
 *       refunds; {@code netRevenue} is their difference.
 *   <li>{@code ratingDistribution} has one entry for every rating from 1 to 5.
 * </ul>
 */
public record SalesSummary(
        long orderCount,
        long deliveredCount,
        long cancelledCount,
        long notPickedUpCount,
        long unitsSold,
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        Map<Integer, Long> ratingDistribution) {

    public SalesSummary {
        Map<Integer, Long> complete = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            complete.put(rating, ratingDistribution.getOrDefault(rating, 0L));
        }
        ratingDistribution = Collections.unmodifiableMap(complete);
    }

    public BigDecimal netRevenue() {
        return grossRevenue.subtract(refundedAmount);
    }

    public long ratingCount() {
        return ratingDistribution.values().stream().mapToLong(Long::longValue).sum();
    }

    /** The average rating with two decimals, or {@code null} when the range has no reviews. */
    public BigDecimal averageRating() {
        long count = ratingCount();
        if (count == 0) {
            return null;
        }
        long sum = ratingDistribution.entrySet().stream()
                .mapToLong(entry -> entry.getKey() * entry.getValue())
                .sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
