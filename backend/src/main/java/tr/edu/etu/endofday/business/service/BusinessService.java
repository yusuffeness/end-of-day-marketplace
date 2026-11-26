package tr.edu.etu.endofday.business.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.BusinessType;
import tr.edu.etu.endofday.business.repository.BusinessRepository;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.web.Paging;

/** Business applications and the owner's management of the business details. */
@Service
public class BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessService.class);

    private final BusinessRepository businessRepository;
    private final BusinessAccess businessAccess;

    public BusinessService(BusinessRepository businessRepository, BusinessAccess businessAccess) {
        this.businessRepository = businessRepository;
        this.businessAccess = businessAccess;
    }

    /**
     * Records an application of the signed-in user, who becomes yetkili_id; it starts as BEKLEMEDE. The owner role is
     * granted only when an administrator approves it. The values must already be validated and trimmed.
     */
    @Transactional
    public Business apply(
            long userId, String name, BusinessType type, String city, String district, String address, String phone) {
        long id = businessRepository.insertApplication(userId, name, type, city, district, address, phone);
        log.info("User {} applied for business {}", userId, id);
        return businessRepository.findById(id).orElseThrow();
    }

    /** The user's own businesses in every approval state, newest first. */
    @Transactional(readOnly = true)
    public BusinessPage listOwn(long userId, Paging paging) {
        return new BusinessPage(
                businessRepository.findByOwner(userId, paging.size(), paging.offset()),
                businessRepository.countByOwner(userId));
    }

    /**
     * Replaces the name, location and phone of an owned business.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-403} when the user does not own it
     */
    @Transactional
    public Business update(
            long userId, long businessId, String name, String city, String district, String address, String phone) {
        businessAccess.requireOwned(userId, businessId);
        businessRepository.updateDetails(businessId, name, city, district, address, phone);
        return businessRepository.findById(businessId).orElseThrow();
    }

    /** A page of businesses with the total number of matches. */
    public record BusinessPage(List<Business> items, long totalItems) {

        public BusinessPage {
            items = List.copyOf(items);
        }
    }
}
