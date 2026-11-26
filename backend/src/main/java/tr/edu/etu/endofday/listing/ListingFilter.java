package tr.edu.etu.endofday.listing;

import java.math.BigDecimal;
import tr.edu.etu.endofday.business.BusinessType;

/** Optional filters of the listing search; a {@code null} value does not filter. */
public record ListingFilter(
        String city, String district, BusinessType type, BigDecimal minPrice, BigDecimal maxPrice) {}
