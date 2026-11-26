package tr.edu.etu.endofday.listing.controller;

import java.time.LocalDateTime;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.listing.ListingSummary;

/** An item of GET /listings. */
public record ListingResponse(
        long id,
        long businessId,
        String businessName,
        String businessType,
        String city,
        String district,
        String title,
        String salePrice,
        String normalPrice,
        int remainingQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd) {

    public static ListingResponse from(ListingSummary listing) {
        return new ListingResponse(
                listing.id(),
                listing.businessId(),
                listing.businessName(),
                listing.businessType().name(),
                listing.city(),
                listing.district(),
                listing.title(),
                Money.format(listing.salePrice()),
                Money.format(listing.normalPrice()),
                listing.remainingQuantity(),
                listing.pickupStart(),
                listing.pickupEnd());
    }
}
