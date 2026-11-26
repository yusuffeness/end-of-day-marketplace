package tr.edu.etu.endofday.business.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.repository.BusinessRepository;
import tr.edu.etu.endofday.common.error.ApiException;

/**
 * The ownership check of every business endpoint (report section 7.3): the signed-in user must be ISLETME.yetkili_id of
 * the business in the path. The ISLETME_YETKILISI role itself is checked by the controllers.
 */
@Component
public class BusinessAccess {

    private final BusinessRepository businessRepository;

    public BusinessAccess(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    /**
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-403} when the user does not own it
     */
    public Business requireOwned(long userId, long businessId) {
        return check(userId, businessRepository.findById(businessId));
    }

    /** Like {@link #requireOwned}, with a shared lock on the business row. */
    public Business requireOwnedForShare(long userId, long businessId) {
        return check(userId, businessRepository.findByIdForShare(businessId));
    }

    private static Business check(long userId, Optional<Business> business) {
        Business found = business.orElseThrow(ApiException::notFound);
        if (!found.isOwnedBy(userId)) {
            throw ApiException.forbidden();
        }
        return found;
    }
}
