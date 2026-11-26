package tr.edu.etu.endofday.pickup.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.order.controller.OrderResponse;
import tr.edu.etu.endofday.pickup.service.PickupService;

/** Verification of pickup codes by the owner of the business. */
@RestController
@PreAuthorize("hasRole('ISLETME_YETKILISI')")
public class PickupController {

    private final PickupService pickupService;

    public PickupController(PickupService pickupService) {
        this.pickupService = pickupService;
    }

    @PostMapping("/api/v1/businesses/{businessId}/pickups/verify")
    public OrderResponse verify(
            @PathVariable long businessId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody VerifyPickupRequest request) {
        return OrderResponse.from(pickupService.verify(user.id(), businessId, request.code()));
    }

    /** Body of POST /businesses/{id}/pickups/verify. The code is never logged, so toString leaves it out. */
    public record VerifyPickupRequest(
            @NotNull(message = "Teslim kodu zorunludur.")
            @Pattern(regexp = "^[0-9]{6}$", message = "Teslim kodu 6 haneli olmalıdır.")
            String code) {

        public VerifyPickupRequest {
            code = TextInputs.trim(code);
        }

        @Override
        public String toString() {
            return "VerifyPickupRequest[]";
        }
    }
}
