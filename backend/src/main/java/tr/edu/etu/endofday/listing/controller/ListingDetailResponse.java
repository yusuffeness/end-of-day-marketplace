package tr.edu.etu.endofday.listing.controller;

import java.time.LocalDateTime;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.listing.ListingDetail;

/** The body of GET /listings/{id} and of a published listing. */
public record ListingDetailResponse(
        long id,
        long businessId,
        String businessName,
        String businessType,
        String city,
        String district,
        String address,
        String phone,
        String title,
        String description,
        String allergenInfo,
        String salePrice,
        String normalPrice,
        int totalQuantity,
        int remainingQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        String status,
        LocalDateTime createdAt) {

    public static ListingDetailResponse from(ListingDetail listing) {
        Business business = listing.business();
        return new ListingDetailResponse(
                listing.id(),
                business.id(),
                business.name(),
                business.type().name(),
                business.city(),
                business.district(),
                business.address(),
                business.phone(),
                listing.title(),
                listing.description(),
                listing.allergenInfo(),
                Money.format(listing.salePrice()),
                Money.format(listing.normalPrice()),
                listing.totalQuantity(),
                listing.remainingQuantity(),
                listing.pickupStart(),
                listing.pickupEnd(),
                listing.status().name(),
                listing.createdAt());
    }
}
