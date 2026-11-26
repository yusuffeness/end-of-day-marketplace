package tr.edu.etu.endofday.review.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;
import tr.edu.etu.endofday.order.LockedOrder;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.repository.OrderRepository;
import tr.edu.etu.endofday.review.repository.ReviewRepository;

/** Reviews of delivered orders by their customer. */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository, OrderRepository orderRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * Records the customer's review of a delivered order. The order row is locked, so two concurrent reviews of the
     * same order are serialized and the second one finds the first.
     *
     * @throws ApiException {@code EOD-404} when the order does not exist or belongs to someone else, {@code EOD-006}
     *     when it is not delivered, {@code EOD-024} when it already has a review
     */
    @Transactional
    public Review review(long customerId, long orderId, int rating, String comment) {
        LockedOrder order = orderRepository
                .lockById(orderId)
                .filter(locked -> locked.customerId() == customerId)
                .orElseThrow(ApiException::notFound);
        if (order.status() != OrderStatus.TESLIM_EDILDI) {
            throw RuleErrors.orderNotDelivered();
        }
        if (reviewRepository.existsForOrder(orderId)) {
            throw RuleErrors.alreadyReviewed();
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        try {
            reviewRepository.insert(orderId, rating, comment, now);
        } catch (DuplicateKeyException ex) {
            throw RuleErrors.alreadyReviewed();
        }
        return new Review(orderId, rating, comment, now);
    }

    /** A stored review. */
    public record Review(long orderId, int rating, String comment, LocalDateTime createdAt) {}
}
