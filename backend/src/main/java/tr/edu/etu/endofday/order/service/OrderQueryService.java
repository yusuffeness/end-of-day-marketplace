package tr.edu.etu.endofday.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.order.OrderResult;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.OrderView;
import tr.edu.etu.endofday.order.repository.OrderRepository;

/** Reads orders for their customer and evaluates which actions are possible at the current time. */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderQueryService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * The customer's own order. Another customer's order is answered like a missing one, so that its existence is not
     * revealed.
     *
     * @throws ApiException {@code EOD-404} when the order does not exist or belongs to someone else
     */
    @Transactional(readOnly = true)
    public OrderResult getOwn(long customerId, long orderId) {
        OrderView order = orderRepository
                .findView(orderId)
                .filter(view -> view.customerId() == customerId)
                .orElseThrow(ApiException::notFound);
        return evaluate(order);
    }

    /** A page of the customer's orders, newest first; the items are not included. */
    @Transactional(readOnly = true)
    public OrderPage listOwn(long customerId, OrderStatus status, Paging paging) {
        List<OrderResult> items =
                orderRepository.findViewsOfCustomer(customerId, status, paging.size(), paging.offset()).stream()
                        .map(this::evaluate)
                        .toList();
        return new OrderPage(items, orderRepository.countOfCustomer(customerId, status));
    }

    /** The order with its items, read in the caller's transaction; the caller has checked the access. */
    public OrderResult read(long orderId) {
        return evaluate(orderRepository.findView(orderId).orElseThrow(ApiException::notFound));
    }

    private OrderResult evaluate(OrderView order) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new OrderResult(order, order.isCancellableAt(now), order.isReviewable());
    }

    /** A page of orders with the total number of matches. */
    public record OrderPage(List<OrderResult> items, long totalItems) {

        public OrderPage {
            items = List.copyOf(items);
        }
    }
}
