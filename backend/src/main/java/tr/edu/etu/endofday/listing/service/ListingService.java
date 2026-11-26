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
import tr.edu.etu.endofday.business.service.BusinessAccess;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.listing.ListingDetail;
import tr.edu.etu.endofday.listing.ListingFilter;
import tr.edu.etu.endofday.listing.ListingStatus;
import tr.edu.etu.endofday.listing.ListingSummary;
import tr.edu.etu.endofday.listing.LockedListing;
import tr.edu.etu.endofday.listing.NewListing;
import tr.edu.etu.endofday.listing.repository.ListingRepository;
import tr.edu.etu.endofday.user.Role;

/** The listing search, listing details and the owner's management of the listings of a business. */
@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final BusinessAccess businessAccess;
    private final Clock clock;

    public ListingService(ListingRepository listingRepository, BusinessAccess businessAccess, Clock clock) {
        this.listingRepository = listingRepository;
        this.businessAccess = businessAccess;
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
     * Publishes a listing of an owned business. The amounts and texts must already be validated. The pickup window must
     * start in the future and end on the same day (report section 1.3: every listing belongs to one day).
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
        Business business = businessAccess.requireOwnedForShare(userId, businessId);
        if (!business.isOpenForSale()) {
            throw RuleErrors.businessNotOpenForListings();
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!pickupStart.isAfter(now)
                || !pickupStart.isBefore(pickupEnd)
                || !pickupStart.toLocalDate().equals(pickupEnd.toLocalDate())) {
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

    /**
     * Every listing of an owned business, active and inactive, newest first.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-403} when the user does not own it
     */
    @Transactional(readOnly = true)
    public DetailPage listOfBusiness(long userId, long businessId, ListingStatus status, Paging paging) {
        businessAccess.requireOwned(userId, businessId);
        return new DetailPage(
                listingRepository.findForInspection(businessId, status, null, null, paging.size(), paging.offset()),
                listingRepository.countForInspection(businessId, status, null, null));
    }

    /**
     * Changes the sale price of a listing of an owned business for future orders. The listing row is locked, so a
     * concurrent order uses either the old or the new price; existing order items keep their unit price.
     *
     * @throws ApiException {@code EOD-404} for an unknown business or a listing of another business, {@code EOD-403}
     *     when the user does not own the business, {@code EOD-029} when the price is not below the normal price
     */
    @Transactional
    public ListingDetail changePrice(long userId, long businessId, long listingId, BigDecimal salePrice) {
        businessAccess.requireOwned(userId, businessId);
        lockListingOf(businessId, listingId);
        ListingDetail listing = listingRepository.findDetail(listingId).orElseThrow(ApiException::notFound);
        if (salePrice.signum() <= 0 || salePrice.compareTo(listing.normalPrice()) >= 0) {
            throw RuleErrors.salePriceNotDiscounted();
        }
        listingRepository.updateSalePrice(listingId, salePrice);
        return listingRepository.findDetail(listingId).orElseThrow();
    }

    /**
     * Sets a listing of an owned business to PASIF. Confirmed orders are not affected; repeating it has no further
     * effect.
     *
     * @throws ApiException {@code EOD-404} for an unknown business or a listing of another business, {@code EOD-403}
     *     when the user does not own the business
     */
    @Transactional
    public ListingDetail deactivate(long userId, long businessId, long listingId) {
        businessAccess.requireOwned(userId, businessId);
        lockListingOf(businessId, listingId);
        listingRepository.deactivate(listingId);
        return listingRepository.findDetail(listingId).orElseThrow();
    }

    /** Locks the listing; a listing of another business is answered like a missing one. */
    private LockedListing lockListingOf(long businessId, long listingId) {
        return listingRepository.lockInIdOrder(List.of(listingId)).stream()
                .filter(listing -> listing.businessId() == businessId)
                .findFirst()
                .orElseThrow(ApiException::notFound);
    }

    /** A page of listings with the total number of matches. */
    public record ListingPage(List<ListingSummary> items, long totalItems) {

        public ListingPage {
            items = List.copyOf(items);
        }
    }

    /** A page of listing details with the total number of matches. */
    public record DetailPage(List<ListingDetail> items, long totalItems) {

        public DetailPage {
            items = List.copyOf(items);
        }
    }
}
