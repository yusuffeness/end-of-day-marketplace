package tr.edu.etu.endofday.listing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.repository.BusinessRepository;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.listing.ListingDetail;
import tr.edu.etu.endofday.listing.ListingFilter;
import tr.edu.etu.endofday.listing.ListingStatus;
import tr.edu.etu.endofday.listing.ListingSummary;
import tr.edu.etu.endofday.listing.NewListing;
import tr.edu.etu.endofday.listing.repository.ListingRepository;
import tr.edu.etu.endofday.user.Role;

/** The listing search, listing details and publishing a listing. */
@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final BusinessRepository businessRepository;
    private final Clock clock;

    public ListingService(ListingRepository listingRepository, BusinessRepository businessRepository, Clock clock) {
        this.listingRepository = listingRepository;
        this.businessRepository = businessRepository;
        this.clock = clock;
    }

    /** A page of the purchasable listings, ordered by pickup start. */
    @Transactional(readOnly = true)
    public ListingPage search(ListingFilter filter, Paging paging) {
        if (filter.minPrice() != null
                && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw ApiException.badRequest();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<ListingSummary> items = listingRepository.findPurchasable(filter, now, paging.size(), paging.offset());
        long total = listingRepository.countPurchasable(filter, now);
        return new ListingPage(items, total);
    }

    /**
     * Reads a listing. A listing that is inactive, or whose business is not approved and active, is visible only to the
     * owner of the business and to administrators; everyone else gets {@code EOD-404}. Listings that have only sold out
     * or whose pickup window has started stay visible, so that customers can look up the listings of their orders.
     */
    @Transactional(readOnly = true)
    public ListingDetail get(long id, AuthenticatedUser viewer) {
        ListingDetail listing = listingRepository.findDetail(id).orElseThrow(ApiException::notFound);
        boolean published =
                listing.status() == ListingStatus.AKTIF && listing.business().isOpenForSale();
        boolean privileged =
                listing.business().isOwnedBy(viewer.id()) || viewer.roles().contains(Role.YONETICI);
        if (!published && !privileged) {
            throw ApiException.notFound();
        }
        return listing;
    }

    /**
     * Publishes a listing of the business. The amounts and texts must already be validated.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-403} when the user does not own it,
     *     {@code EOD-007} when it is not approved and active, {@code EOD-025} for an invalid pickup window
     */
    @Transactional
    public ListingDetail create(
            long userId,
            long businessId,
            String title,
            String description,
            String allergenInfo,
            BigDecimal normalPrice,
            BigDecimal salePrice,
            int totalQuantity,
            LocalDateTime pickupStart,
            LocalDateTime pickupEnd) {
        Business business = businessRepository.findByIdForShare(businessId).orElseThrow(ApiException::notFound);
        if (!business.isOwnedBy(userId)) {
            throw ApiException.forbidden();
        }
        if (!business.isOpenForSale()) {
            throw RuleErrors.businessNotOpenForListings();
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!pickupStart.isBefore(pickupEnd) || !pickupEnd.isAfter(now)) {
            throw RuleErrors.invalidPickupWindow();
        }
        long id = listingRepository.insert(new NewListing(
                businessId,
                title,
                description,
                allergenInfo,
                normalPrice,
                salePrice,
                totalQuantity,
                pickupStart,
                pickupEnd,
                now));
        return listingRepository.findDetail(id).orElseThrow();
    }

    /** A page of listings with the total number of matches. */
    public record ListingPage(List<ListingSummary> items, long totalItems) {

        public ListingPage {
            items = List.copyOf(items);
        }
    }
}
