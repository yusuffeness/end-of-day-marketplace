package tr.edu.etu.endofday.listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import tr.edu.etu.endofday.business.BusinessType;

/** A purchasable listing with the location of its business, as shown in the listing search. */
public record ListingSummary(
        long id,
        long businessId,
        String businessName,
        BusinessType businessType,
        String city,
        String district,
        String title,
        BigDecimal salePrice,
        BigDecimal normalPrice,
        int remainingQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd) {}
