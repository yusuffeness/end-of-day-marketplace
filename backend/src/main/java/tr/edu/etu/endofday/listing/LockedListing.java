package tr.edu.etu.endofday.listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A listing row read with an exclusive lock while an order is placed. */
public record LockedListing(
        long id,
        long businessId,
        BigDecimal salePrice,
        int remainingQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        ListingStatus status) {}
