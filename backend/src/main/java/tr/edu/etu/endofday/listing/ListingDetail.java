package tr.edu.etu.endofday.listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import tr.edu.etu.endofday.business.Business;

/** A listing with every column and its business. */
public record ListingDetail(
        long id,
        Business business,
        String title,
        String description,
        String allergenInfo,
        BigDecimal normalPrice,
        BigDecimal salePrice,
        int totalQuantity,
        int remainingQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        ListingStatus status,
        LocalDateTime createdAt) {

    /**
     * A listing can be bought when it is active, has stock left, belongs to an approved and active business and its
     * pickup window has not started.
     */
    public boolean isPurchasableAt(LocalDateTime now) {
        return status == ListingStatus.AKTIF
                && business.isOpenForSale()
                && remainingQuantity > 0
                && now.isBefore(pickupStart);
    }
}
