package tr.edu.etu.endofday.business.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
import tr.edu.etu.endofday.business.service.BusinessOrderService;
import tr.edu.etu.endofday.business.service.BusinessService;
import tr.edu.etu.endofday.common.web.PageResponse;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.order.OrderStatus;

/** Business applications and the management of an owned business. */
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final BusinessOrderService businessOrderService;

    public BusinessController(BusinessService businessService, BusinessOrderService businessOrderService) {
        this.businessService = businessService;
        this.businessOrderService = businessOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public BusinessResponse apply(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ApplyBusinessRequest request) {
        return BusinessResponse.from(businessService.apply(
                user.id(),
                request.name(),
                request.type(),
                request.city(),
                request.district(),
                request.address(),
                request.phone()));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<BusinessResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        BusinessService.BusinessPage result = businessService.listOwn(user.id(), paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), BusinessResponse::from);
    }

    @PutMapping("/{businessId}")
    @PreAuthorize("hasRole('ISLETME_YETKILISI')")
    public BusinessResponse update(
            @PathVariable long businessId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateBusinessRequest request) {
        return BusinessResponse.from(businessService.update(
                user.id(),
                businessId,
                request.name(),
                request.city(),
                request.district(),
                request.address(),
                request.phone()));
    }

    @GetMapping("/{businessId}/orders")
    @PreAuthorize("hasRole('ISLETME_YETKILISI')")
    public PageResponse<BusinessOrderResponse> orders(
            @PathVariable long businessId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        BusinessOrderService.BusinessOrderPage result =
                businessOrderService.list(user.id(), businessId, date, status, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), BusinessOrderResponse::from);
    }
}
