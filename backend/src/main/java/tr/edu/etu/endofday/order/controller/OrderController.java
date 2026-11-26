package tr.edu.etu.endofday.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.common.web.PageResponse;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.service.OrderQueryService;
import tr.edu.etu.endofday.order.service.OrderService;

/** The customer's orders: placing, listing, viewing and cancelling. */
@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('MUSTERI')")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PlaceOrderRequest request) {
        return OrderResponse.from(orderService.place(user.id(), request.toCommand()));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        OrderQueryService.OrderPage result = orderQueryService.listOwn(user.id(), status, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), OrderResponse::withoutItems);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long orderId) {
        return OrderResponse.from(orderQueryService.getOwn(user.id(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        return OrderResponse.from(orderService.cancel(user.id(), orderId, request.requestId(), request.refundFails()));
    }
}
