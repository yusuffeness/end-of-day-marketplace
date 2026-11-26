package tr.edu.etu.endofday.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.admin.repository.AdminUserRepository;
import tr.edu.etu.endofday.business.ApprovalStatus;
import tr.edu.etu.endofday.business.Business;
import tr.edu.etu.endofday.business.BusinessApplication;
import tr.edu.etu.endofday.business.repository.BusinessRepository;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.listing.ListingDetail;
import tr.edu.etu.endofday.listing.ListingStatus;
import tr.edu.etu.endofday.listing.repository.ListingRepository;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.UserProfile;
import tr.edu.etu.endofday.user.repository.UserRoleRepository;

/**
 * The administrator panel: business applications, account and business activity, roles and listing inspection.
 *
 * <p>Changes to accounts and roles take effect on the user's next request, because the session holds only the user id
 * and the active flag and roles are read from the database on every request.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final BusinessRepository businessRepository;
    private final AdminUserRepository adminUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final ListingRepository listingRepository;

    public AdminService(
            BusinessRepository businessRepository,
            AdminUserRepository adminUserRepository,
            UserRoleRepository userRoleRepository,
            ListingRepository listingRepository) {
        this.businessRepository = businessRepository;
        this.adminUserRepository = adminUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.listingRepository = listingRepository;
    }

    /** Applications in one approval state, oldest first. */
    @Transactional(readOnly = true)
    public ApplicationPage applications(ApprovalStatus status, Paging paging) {
        return new ApplicationPage(
                businessRepository.findApplications(status, paging.size(), paging.offset()),
                businessRepository.countApplications(status));
    }

    /**
     * Approves a pending application and grants its owner the ISLETME_YETKILISI role in the same transaction. The
     * business row is locked, so two administrators cannot decide the same application twice.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-026} when it is not BEKLEMEDE
     */
    @Transactional
    public Business approve(long businessId) {
        Business business = lockPending(businessId);
        businessRepository.setApprovalStatus(businessId, ApprovalStatus.ONAYLANDI);
        userRoleRepository.addRole(business.ownerId(), Role.ISLETME_YETKILISI);
        log.info("Approved business {} of user {}", businessId, business.ownerId());
        return businessRepository.findById(businessId).orElseThrow();
    }

    /**
     * Rejects a pending application.
     *
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-026} when it is not BEKLEMEDE
     */
    @Transactional
    public Business reject(long businessId) {
        lockPending(businessId);
        businessRepository.setApprovalStatus(businessId, ApprovalStatus.REDDEDILDI);
        log.info("Rejected business {}", businessId);
        return businessRepository.findById(businessId).orElseThrow();
    }

    /** Accounts that match the optional filters, ordered by id. */
    @Transactional(readOnly = true)
    public UserPage users(String text, Role role, Boolean active, Paging paging) {
        return new UserPage(
                adminUserRepository.search(text, role, active, paging.size(), paging.offset()),
                adminUserRepository.count(text, role, active));
    }

    /**
     * Activates or deactivates an account. A deactivated user's session is rejected on the next request.
     *
     * @throws ApiException {@code EOD-404} for an unknown user, {@code EOD-027} when administrators deactivate
     *     themselves
     */
    @Transactional
    public void setUserActive(long adminId, long userId, boolean active) {
        if (!active && adminId == userId) {
            throw RuleErrors.cannotDeactivateOwnAccount();
        }
        if (!adminUserRepository.setActive(userId, active)) {
            throw ApiException.notFound();
        }
        log.info("Set account {} active={}", userId, active);
    }

    /**
     * Activates or deactivates a business. The listings of an inactive business leave the listing search and take no
     * orders; they become available again when the business is activated.
     *
     * @throws ApiException {@code EOD-404} for an unknown business
     */
    @Transactional
    public Business setBusinessActive(long businessId, boolean active) {
        businessRepository.lockById(businessId).orElseThrow(ApiException::notFound);
        businessRepository.setActive(businessId, active);
        log.info("Set business {} active={}", businessId, active);
        return businessRepository.findById(businessId).orElseThrow();
    }

    /**
     * Grants a role; granting a role the user holds has no effect.
     *
     * @throws ApiException {@code EOD-404} for an unknown user or role name
     */
    @Transactional
    public void grantRole(long userId, String roleName) {
        Role role = parseRole(roleName);
        if (!adminUserRepository.exists(userId)) {
            throw ApiException.notFound();
        }
        userRoleRepository.addRole(userId, role);
        log.info("Granted role {} to user {}", role, userId);
    }

    /**
     * Revokes a role from KULLANICI_ROL; revoking a role the user does not hold has no effect.
     *
     * @throws ApiException {@code EOD-404} for an unknown user or role name, {@code EOD-028} when administrators
     *     revoke their own YONETICI role
     */
    @Transactional
    public void revokeRole(long adminId, long userId, String roleName) {
        Role role = parseRole(roleName);
        if (role == Role.YONETICI && adminId == userId) {
            throw RuleErrors.cannotRevokeOwnAdministratorRole();
        }
        if (!adminUserRepository.exists(userId)) {
            throw ApiException.notFound();
        }
        userRoleRepository.removeRole(userId, role);
        log.info("Revoked role {} from user {}", role, userId);
    }

    /**
     * Every listing that matches the optional filters, newest first.
     *
     * @param from first day of the pickup start range, or {@code null}
     * @param to last day of the pickup start range (inclusive), or {@code null}
     */
    @Transactional(readOnly = true)
    public ListingPage listings(Long businessId, ListingStatus status, LocalDate from, LocalDate to, Paging paging) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest();
        }
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        return new ListingPage(
                listingRepository.findForInspection(businessId, status, start, end, paging.size(), paging.offset()),
                listingRepository.countForInspection(businessId, status, start, end));
    }

    /**
     * Sets any listing to PASIF, for example when it breaks the platform rules.
     *
     * @throws ApiException {@code EOD-404} for an unknown listing
     */
    @Transactional
    public ListingDetail deactivateListing(long listingId) {
        if (listingRepository.lockInIdOrder(List.of(listingId)).isEmpty()) {
            throw ApiException.notFound();
        }
        listingRepository.deactivate(listingId);
        log.info("Deactivated listing {}", listingId);
        return listingRepository.findDetail(listingId).orElseThrow();
    }

    private Business lockPending(long businessId) {
        Business business = businessRepository.lockById(businessId).orElseThrow(ApiException::notFound);
        if (business.approvalStatus() != ApprovalStatus.BEKLEMEDE) {
            throw RuleErrors.applicationNotPending();
        }
        return business;
    }

    private static Role parseRole(String roleName) {
        for (Role role : Role.values()) {
            if (role.name().equals(roleName)) {
                return role;
            }
        }
        throw ApiException.notFound();
    }

    /** A page of applications with the total number of matches. */
    public record ApplicationPage(List<BusinessApplication> items, long totalItems) {

        public ApplicationPage {
            items = List.copyOf(items);
        }
    }

    /** A page of accounts with the total number of matches. */
    public record UserPage(List<UserProfile> items, long totalItems) {

        public UserPage {
            items = List.copyOf(items);
        }
    }

    /** A page of listings with the total number of matches. */
    public record ListingPage(List<ListingDetail> items, long totalItems) {

        public ListingPage {
            items = List.copyOf(items);
        }
    }
}
