package tr.edu.etu.endofday.listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The values of a listing to insert; it starts as AKTIF with all units remaining. */
public record NewListing(
        long businessId,
        String title,
        String description,
        String allergenInfo,
        BigDecimal normalPrice,
        BigDecimal salePrice,
        int totalQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        LocalDateTime createdAt) {}
