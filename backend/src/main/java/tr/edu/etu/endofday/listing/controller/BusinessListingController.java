package tr.edu.etu.endofday.listing.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.listing.service.ListingService;

/** Listings managed by the owner of a business. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/listings")
@PreAuthorize("hasRole('ISLETME_YETKILISI')")
public class BusinessListingController {

    private final ListingService listingService;

    public BusinessListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingDetailResponse create(
            @PathVariable long businessId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateListingRequest request) {
        return ListingDetailResponse.from(listingService.create(
                user.id(),
                businessId,
                request.title(),
                request.description(),
                request.allergenInfo(),
                request.normalPriceAmount(),
                request.salePriceAmount(),
                request.totalQuantity(),
                request.pickupStart(),
                request.pickupEnd()));
    }
}
