package tr.edu.etu.endofday.order.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.repository.BusinessRepository;
import tr.edu.etu.endofday.common.Transactions;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;
import tr.edu.etu.endofday.listing.ListingStatus;
import tr.edu.etu.endofday.listing.LockedListing;
import tr.edu.etu.endofday.listing.repository.ListingRepository;
import tr.edu.etu.endofday.order.LockedOrder;
import tr.edu.etu.endofday.order.OrderLine;
import tr.edu.etu.endofday.order.OrderResult;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.repository.OrderRepository;
import tr.edu.etu.endofday.payment.PaymentTransaction;
import tr.edu.etu.endofday.payment.PaymentType;
import tr.edu.etu.endofday.payment.repository.PaymentRepository;
import tr.edu.etu.endofday.payment.service.PaymentService;
import tr.edu.etu.endofday.pickup.PickupRecord;
import tr.edu.etu.endofday.pickup.repository.PickupRepository;
import tr.edu.etu.endofday.pickup.service.PickupService;

/**
 * Placing and cancelling orders (report section 7.2). Each runs in one READ COMMITTED transaction that first locks
 * the rows it changes; the rules that depend on the current time are checked here, with the time of the Clock bean,
 * and the cross-table rules are checked again by the triggers.
 *
 * <p>A request id (ODEME_ISLEMI.istek_no) is processed at most once. A repeated request returns the result of the
 * first one; when two requests with the same id run at the same time, the UNIQUE constraint lets only one of them
 * commit and the other one reads its result.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final BusinessRepository businessRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PickupRepository pickupRepository;
    private final PickupService pickupService;
    private final OrderQueryService orderQueryService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public OrderService(
            BusinessRepository businessRepository,
            ListingRepository listingRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            PickupRepository pickupRepository,
            PickupService pickupService,
            OrderQueryService orderQueryService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.businessRepository = businessRepository;
        this.listingRepository = listingRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.pickupRepository = pickupRepository;
        this.pickupService = pickupService;
        this.orderQueryService = orderQueryService;
        this.transaction = Transactions.readCommitted(transactionManager);
        this.clock = clock;
    }

    /**
     * Places and charges an order in one transaction. Quantities of the same listing are added up into one item. When
     * the simulated payment is declined, the transaction is rolled back and no table keeps a trace of the order.
     *
     * @throws ApiException {@code EOD-404} for an unknown business or listing, {@code EOD-013} when the business or a
     *     listing is not open for sale, {@code EOD-001} for a listing of another business, {@code EOD-014} for
     *     insufficient stock, {@code EOD-015} when the pickup windows have no common time, {@code EOD-016} when the
     *     common window has started, {@code EOD-017} when the payment is declined, {@code EOD-018} when the request
     *     id belongs to another request
     */
    public OrderResult place(long customerId, PlaceOrder command) {
        Optional<OrderResult> earlier = transaction.execute(status -> findPlacedOrder(customerId, command.requestId()));
        if (earlier.isPresent()) {
            return earlier.get();
        }
        try {
            return transaction.execute(status -> placeInTransaction(customerId, command));
        } catch (DuplicateKeyException ex) {
            // A concurrent request with the same request id committed its charge first.
            return transaction
                    .execute(status -> findPlacedOrder(customerId, command.requestId()))
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Cancels a confirmed order before its pickup window starts: the full charge is refunded, the stock is returned
     * once and the status becomes IPTAL_EDILDI, all in one transaction. A declined refund is kept as BASARISIZ and the
     * order stays confirmed.
     *
     * @throws ApiException {@code EOD-404} when the order does not exist or belongs to someone else, {@code EOD-005}
     *     when it is not confirmed, {@code EOD-019} when its pickup window has started, {@code EOD-020} when the
     *     refund is declined, {@code EOD-018} when the request id belongs to another request
     */
    public OrderResult cancel(long customerId, long orderId, String requestId, boolean simulateRefundFailure) {
        Optional<CancelOutcome> earlier =
                transaction.execute(status -> findCancellation(customerId, orderId, requestId));
        CancelOutcome outcome;
        if (earlier.isPresent()) {
            outcome = earlier.get();
        } else {
            try {
                outcome = transaction.execute(
                        status -> cancelInTransaction(customerId, orderId, requestId, simulateRefundFailure));
            } catch (DuplicateKeyException ex) {
                outcome = transaction
                        .execute(status -> findCancellation(customerId, orderId, requestId))
                        .orElseThrow(() -> ex);
            }
        }
        if (outcome == CancelOutcome.REFUND_DECLINED) {
            throw RuleErrors.refundDeclined();
        }
        return transaction.execute(status -> orderQueryService.read(orderId));
    }

    private OrderResult placeInTransaction(long customerId, PlaceOrder command) {
        SortedMap<Long, Integer> quantities = mergeItems(command.items());

        // Lock order: the business (shared), then the listings in id order (exclusive).
        Business business =
                businessRepository.findByIdForShare(command.businessId()).orElseThrow(ApiException::notFound);
        List<LockedListing> listings = listingRepository.lockInIdOrder(quantities.keySet());
        if (listings.size() != quantities.size()) {
            throw ApiException.notFound();
        }
        // Holding the locks, read again: a concurrent request with this id may have committed meanwhile.
        Optional<OrderResult> earlier = findPlacedOrder(customerId, command.requestId());
        if (earlier.isPresent()) {
            return earlier.get();
        }

        if (!business.isOpenForSale()) {
            throw RuleErrors.listingNotAvailable();
        }
        LocalDateTime windowStart = null;
        LocalDateTime windowEnd = null;
        for (LockedListing listing : listings) {
            if (listing.businessId() != business.id()) {
                throw RuleErrors.listingOfAnotherBusiness();
            }
            if (listing.status() != ListingStatus.AKTIF) {
                throw RuleErrors.listingNotAvailable();
            }
            if (listing.remainingQuantity() < quantities.get(listing.id())) {
                throw RuleErrors.insufficientStock();
            }
            windowStart = windowStart == null || listing.pickupStart().isAfter(windowStart)
                    ? listing.pickupStart()
                    : windowStart;
            windowEnd = windowEnd == null || listing.pickupEnd().isBefore(windowEnd) ? listing.pickupEnd() : windowEnd;
        }
        if (!windowStart.isBefore(windowEnd)) {
            throw RuleErrors.noCommonPickupWindow();
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!now.isBefore(windowStart)) {
            throw RuleErrors.pickupWindowStarted();
        }

        long orderId = orderRepository.insert(customerId, business.id(), now);
        BigDecimal total = BigDecimal.ZERO;
        for (LockedListing listing : listings) {
            int quantity = quantities.get(listing.id());
            // The unit price is the sale price at the time of purchase, read from the locked row.
            orderRepository.insertItem(orderId, listing.id(), quantity, listing.salePrice());
            if (!listingRepository.decreaseStock(listing.id(), quantity)) {
                throw new IllegalStateException("Stock of locked listing " + listing.id() + " changed");
            }
            total = total.add(listing.salePrice().multiply(BigDecimal.valueOf(quantity)));
        }
        if (!paymentService.charge(orderId, command.requestId(), total, command.simulatePaymentFailure(), now)) {
            // Thrown inside the transaction callback, so everything above is rolled back.
            throw RuleErrors.paymentDeclined();
        }
        pickupService.issue(orderId, windowStart, windowEnd);
        log.info("Placed order {} for customer {}", orderId, customerId);
        return orderQueryService.read(orderId);
    }

    private CancelOutcome cancelInTransaction(
            long customerId, long orderId, String requestId, boolean simulateRefundFailure) {
        LockedOrder order = orderRepository
                .lockById(orderId)
                .filter(locked -> locked.customerId() == customerId)
                .orElseThrow(ApiException::notFound);
        Optional<CancelOutcome> earlier = findCancellation(customerId, orderId, requestId);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        if (order.status() != OrderStatus.ONAYLANDI) {
            throw RuleErrors.orderNotCancellable();
        }
        PickupRecord pickup = pickupRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Confirmed order " + orderId + " has no pickup"));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!now.isBefore(pickup.windowStart())) {
            throw RuleErrors.cancellationPeriodOver();
        }
        PaymentTransaction charge = paymentRepository
                .findSuccessfulCharge(orderId)
                .orElseThrow(() -> new IllegalStateException("Confirmed order " + orderId + " has no charge"));

        if (!paymentService.refundInFull(charge, requestId, simulateRefundFailure, now)) {
            // Committed as BASARISIZ; the order stays confirmed and its stock is not touched.
            log.info("Refund for order {} was declined", orderId);
            return CancelOutcome.REFUND_DECLINED;
        }
        for (OrderLine line : orderRepository.findLines(orderId)) {
            listingRepository.increaseStock(line.listingId(), line.quantity());
        }
        if (!orderRepository.changeStatus(orderId, OrderStatus.ONAYLANDI, OrderStatus.IPTAL_EDILDI, now)) {
            throw new IllegalStateException("Locked order " + orderId + " changed its status");
        }
        log.info("Cancelled order {}", orderId);
        return CancelOutcome.CANCELLED;
    }

    /**
     * The order created by an earlier request with this id.
     *
     * @throws ApiException {@code EOD-018} when the id was used by another customer or for another kind of request
     */
    private Optional<OrderResult> findPlacedOrder(long customerId, String requestId) {
        Optional<PaymentTransaction> payment = paymentRepository.findByRequestId(requestId);
        if (payment.isEmpty()) {
            return Optional.empty();
        }
        OrderResult order = orderQueryService.read(payment.get().orderId());
        if (payment.get().type() != PaymentType.TAHSILAT || order.order().customerId() != customerId) {
            throw RuleErrors.requestIdInUse();
        }
        return Optional.of(order);
    }

    /**
     * The outcome of an earlier cancellation request with this id.
     *
     * @throws ApiException {@code EOD-018} when the id was used for another order or another kind of request
     */
    private Optional<CancelOutcome> findCancellation(long customerId, long orderId, String requestId) {
        Optional<PaymentTransaction> payment = paymentRepository.findByRequestId(requestId);
        if (payment.isEmpty()) {
            return Optional.empty();
        }
        if (payment.get().type() != PaymentType.IADE || payment.get().orderId() != orderId) {
            throw RuleErrors.requestIdInUse();
        }
        // The refund belongs to this order; answer only its own customer.
        if (orderQueryService.read(orderId).order().customerId() != customerId) {
            throw ApiException.notFound();
        }
        return Optional.of(payment.get().isSuccessful() ? CancelOutcome.CANCELLED : CancelOutcome.REFUND_DECLINED);
    }

    /** Adds up the quantities per listing, in ascending listing id order. */
    private static SortedMap<Long, Integer> mergeItems(List<PlaceOrder.Item> items) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        for (PlaceOrder.Item item : items) {
            quantities.merge(item.listingId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    private enum CancelOutcome {
        CANCELLED,
        REFUND_DECLINED
    }

    /** A validated order request. */
    public record PlaceOrder(long businessId, List<Item> items, String requestId, boolean simulatePaymentFailure) {

        public PlaceOrder {
            items = List.copyOf(items);
        }

        public record Item(long listingId, int quantity) {}
    }
}
