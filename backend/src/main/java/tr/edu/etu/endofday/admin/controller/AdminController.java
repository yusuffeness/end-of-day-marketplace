package tr.edu.etu.endofday.admin.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.admin.service.AdminService;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.business.ApprovalStatus;
import tr.edu.etu.endofday.business.BusinessApplication;
import tr.edu.etu.endofday.business.controller.BusinessResponse;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.common.web.PageResponse;
import tr.edu.etu.endofday.common.web.Paging;
import tr.edu.etu.endofday.listing.ListingStatus;
import tr.edu.etu.endofday.listing.controller.ListingDetailResponse;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.User;
import tr.edu.etu.endofday.user.UserProfile;

/** The administrator panel (report section 6). Every endpoint requires the YONETICI role. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('YONETICI')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/business-applications")
    public PageResponse<ApplicationResponse> applications(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        AdminService.ApplicationPage result =
                adminService.applications(status == null ? ApprovalStatus.BEKLEMEDE : status, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), ApplicationResponse::from);
    }

    @PostMapping("/businesses/{businessId}/approve")
    public BusinessResponse approve(@PathVariable long businessId) {
        return BusinessResponse.from(adminService.approve(businessId));
    }

    @PostMapping("/businesses/{businessId}/reject")
    public BusinessResponse reject(@PathVariable long businessId) {
        return BusinessResponse.from(adminService.reject(businessId));
    }

    @PutMapping("/businesses/{businessId}/active")
    public BusinessResponse setBusinessActive(
            @PathVariable long businessId, @Valid @RequestBody ActiveRequest request) {
        return BusinessResponse.from(adminService.setBusinessActive(businessId, request.active()));
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        AdminService.UserPage result = adminService.users(TextInputs.trimToNull(q), role, active, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), AdminUserResponse::from);
    }

    @PutMapping("/users/{userId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUserActive(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long userId,
            @Valid @RequestBody ActiveRequest request) {
        adminService.setUserActive(admin.id(), userId, request.active());
    }

    @PutMapping("/users/{userId}/roles/{roleName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantRole(@PathVariable long userId, @PathVariable String roleName) {
        adminService.grantRole(userId, roleName);
    }

    @DeleteMapping("/users/{userId}/roles/{roleName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable long userId,
            @PathVariable String roleName) {
        adminService.revokeRole(admin.id(), userId, roleName);
    }

    @GetMapping("/listings")
    public PageResponse<ListingDetailResponse> listings(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        AdminService.ListingPage result = adminService.listings(businessId, status, from, to, paging);
        return PageResponse.of(result.items(), paging, result.totalItems(), ListingDetailResponse::from);
    }

    @PostMapping("/listings/{listingId}/deactivate")
    public ListingDetailResponse deactivateListing(@PathVariable long listingId) {
        return ListingDetailResponse.from(adminService.deactivateListing(listingId));
    }

    /** Body of the two PUT .../active endpoints. */
    public record ActiveRequest(
            @NotNull(message = "Aktiflik bilgisi zorunludur.")
            Boolean active) {}

    /** An application with its owner. */
    public record ApplicationResponse(
            long id,
            String name,
            String type,
            String city,
            String district,
            String address,
            String phone,
            String approvalStatus,
            boolean active,
            long ownerId,
            String ownerFirstName,
            String ownerLastName) {

        static ApplicationResponse from(BusinessApplication application) {
            BusinessResponse business = BusinessResponse.from(application.business());
            return new ApplicationResponse(
                    business.id(),
                    business.name(),
                    business.type(),
                    business.city(),
                    business.district(),
                    business.address(),
                    business.phone(),
                    business.approvalStatus(),
                    business.active(),
                    application.business().ownerId(),
                    application.ownerFirstName(),
                    application.ownerLastName());
        }
    }

    /** An account as the administrator sees it. */
    public record AdminUserResponse(
            long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            boolean active,
            LocalDateTime registeredAt,
            List<String> roles) {

        static AdminUserResponse from(UserProfile profile) {
            User user = profile.user();
            return new AdminUserResponse(
                    user.id(),
                    user.firstName(),
                    user.lastName(),
                    user.email(),
                    user.phone(),
                    user.active(),
                    user.registeredAt(),
                    profile.roles().stream().map(Role::name).sorted().toList());
        }

        @Override
        public String toString() {
            return "AdminUserResponse[id=" + id + ", active=" + active + ", roles=" + roles + "]";
        }
    }
}
