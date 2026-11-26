package tr.edu.etu.endofday.listing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.common.web.PageResponse;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.listing.ListingStatus;
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

    /** Every listing of the business, active and inactive, newest first. */
    @GetMapping
    public PageResponse<ListingDetailResponse> list(
            @PathVariable long businessId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        ListingService.DetailPage result = listingService.listOfBusiness(user.id(), businessId, status, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), ListingDetailResponse::from);
    }

    @PutMapping("/{listingId}/price")
    public ListingDetailResponse changePrice(
            @PathVariable long businessId,
            @PathVariable long listingId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePriceRequest request) {
        return ListingDetailResponse.from(
                listingService.changePrice(user.id(), businessId, listingId, Money.parse(request.salePrice())));
    }

    @PostMapping("/{listingId}/deactivate")
    public ListingDetailResponse deactivate(
            @PathVariable long businessId,
            @PathVariable long listingId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ListingDetailResponse.from(listingService.deactivate(user.id(), businessId, listingId));
    }

    /** Body of PUT /businesses/{id}/listings/{listingId}/price. */
    public record ChangePriceRequest(
            @NotNull(message = "Satış fiyatı zorunludur.")
            @Pattern(regexp = Money.PATTERN, message = "Satış fiyatı geçerli bir tutar olmalıdır, örneğin \"55.00\".")
            String salePrice) {}
}
