package tr.edu.etu.endofday.pickup.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.repository.BusinessRepository;
import tr.edu.etu.endofday.common.Transactions;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;
import tr.edu.etu.endofday.order.LockedOrder;
import tr.edu.etu.endofday.order.OrderResult;
import tr.edu.etu.endofday.order.OrderStatus;
import tr.edu.etu.endofday.order.repository.OrderRepository;
import tr.edu.etu.endofday.order.service.OrderQueryService;
import tr.edu.etu.endofday.pickup.PickupRecord;
import tr.edu.etu.endofday.pickup.repository.PickupRepository;

/** Pickup codes: issued with each order and verified by the owner of the business. Codes are never logged. */
@Service
public class PickupService {

    /** With a million possible codes a few attempts are enough even when many codes are taken. */
    static final int MAX_CODE_ATTEMPTS = 20;

    private final PickupRepository pickupRepository;
    private final PickupCodeGenerator codeGenerator;
    private final BusinessRepository businessRepository;
    private final OrderRepository orderRepository;
    private final OrderQueryService orderQueryService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PickupService(
            PickupRepository pickupRepository,
            PickupCodeGenerator codeGenerator,
            BusinessRepository businessRepository,
            OrderRepository orderRepository,
            OrderQueryService orderQueryService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.pickupRepository = pickupRepository;
        this.codeGenerator = codeGenerator;
        this.businessRepository = businessRepository;
        this.orderRepository = orderRepository;
        this.orderQueryService = orderQueryService;
        this.transaction = Transactions.readCommitted(transactionManager);
        this.clock = clock;
    }

    /**
     * Records the pickup window of a new order with a unique code, inside the caller's transaction. A code that is
     * already taken (UNIQUE on teslim_kodu) is replaced by a new one.
     */
    public void issue(long orderId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        for (int attempt = 1; ; attempt++) {
            try {
                pickupRepository.insert(orderId, codeGenerator.next(), windowStart, windowEnd);
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt >= MAX_CODE_ATTEMPTS) {
                    throw new IllegalStateException("No free pickup code after " + attempt + " attempts", ex);
                }
            }
        }
    }

    /**
     * Marks the order with this code as delivered and records the verifier, in one transaction. The order row is
     * locked first and both updates are conditional, so a code can be used only once.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-008} when the user does not own it,
     *     {@code EOD-021} when no confirmed order of the business has the code, {@code EOD-023} when the code was
     *     already used, {@code EOD-022} outside the pickup window
     */
    public OrderResult verify(long userId, long businessId, String code) {
        return transaction.execute(status -> {
            Business business = businessRepository.findById(businessId).orElseThrow(ApiException::notFound);
            if (!business.isOwnedBy(userId)) {
                throw RuleErrors.notOwnerOfBusiness();
            }
            PickupRecord pickup = pickupRepository
                    .findByCode(code)
                    .filter(record -> record.businessId() == businessId)
                    .orElseThrow(RuleErrors::pickupCodeNotFound);
            LockedOrder order = orderRepository.lockById(pickup.orderId()).orElseThrow(RuleErrors::pickupCodeNotFound);
            if (order.status() == OrderStatus.TESLIM_EDILDI) {
                throw RuleErrors.pickupCodeUsed();
            }
            if (order.status() != OrderStatus.ONAYLANDI) {
                throw RuleErrors.pickupCodeNotFound();
            }
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            if (!pickup.isWithinWindow(now)) {
                throw RuleErrors.outsidePickupWindow();
            }
            if (!orderRepository.changeStatus(order.id(), OrderStatus.ONAYLANDI, OrderStatus.TESLIM_EDILDI, null)
                    || !pickupRepository.markPickedUp(order.id(), now, userId)) {
                throw RuleErrors.pickupCodeUsed();
            }
            return orderQueryService.read(order.id());
        });
    }
}
