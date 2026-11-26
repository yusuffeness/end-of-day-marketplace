package tr.edu.etu.endofday.user.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.user.service.UserService;

/** The signed-in user's own account. */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.from(userService.getProfile(user.id()));
    }

    @PutMapping
    public UserResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(
                userService.updateProfile(user.id(), request.firstName(), request.lastName(), request.phone()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request.currentPassword(), request.newPassword());
    }
}
