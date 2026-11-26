package tr.edu.etu.endofday.business.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.business.BusinessOrder;
import tr.edu.etu.endofday.business.repository.BusinessOrderRepository;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.repository.OrderRepository;

/** The orders of a business for its owner, by the day their pickup window starts. */
@Service
public class BusinessOrderService {

    private final BusinessAccess businessAccess;
    private final BusinessOrderRepository businessOrderRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public BusinessOrderService(
            BusinessAccess businessAccess,
            BusinessOrderRepository businessOrderRepository,
            OrderRepository orderRepository,
            Clock clock) {
        this.businessAccess = businessAccess;
        this.businessOrderRepository = businessOrderRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * The orders whose pickup window starts on {@code day} (today when {@code null}), optionally with one status, for
     * example TESLIM_ALINMADI for the orders of the day that were not picked up.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-403} when the user does not own it
     */
    @Transactional(readOnly = true)
    public BusinessOrderPage list(long userId, long businessId, LocalDate day, OrderStatus status, Paging paging) {
        businessAccess.requireOwned(userId, businessId);
        LocalDateTime dayStart = (day == null ? LocalDate.now(clock) : day).atStartOfDay();
        List<BusinessOrder> orders =
                businessOrderRepository
                        .findByPickupDay(businessId, dayStart, status, paging.size(), paging.offset())
                        .stream()
                        .map(order -> order.withItems(orderRepository.findLines(order.id())))
                        .toList();
        return new BusinessOrderPage(orders, businessOrderRepository.countByPickupDay(businessId, dayStart, status));
    }

    /** A page of business orders with the total number of matches. */
    public record BusinessOrderPage(List<BusinessOrder> items, long totalItems) {

        public BusinessOrderPage {
            items = List.copyOf(items);
        }
    }
}
