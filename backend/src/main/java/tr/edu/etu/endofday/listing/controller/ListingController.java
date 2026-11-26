package tr.edu.etu.endofday.listing.controller;

import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.business.BusinessType;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.common.web.PageResponse;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.listing.ListingFilter;
import tr.edu.etu.endofday.listing.service.ListingService;

/** The customer's view of the listings. */
@RestController
@RequestMapping("/api/v1/listings")
@PreAuthorize("isAuthenticated()")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public PageResponse<ListingResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) BusinessType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        ListingFilter filter = new ListingFilter(
                TextInputs.trimToNull(city), TextInputs.trimToNull(district), type, minPrice, maxPrice);
        ListingService.ListingPage result = listingService.search(filter, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), ListingResponse::from);
    }

    @GetMapping("/{id}")
    public ListingDetailResponse get(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ListingDetailResponse.from(listingService.get(id, user));
    }
}
