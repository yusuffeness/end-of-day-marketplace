package tr.edu.etu.endofday.user.controller;

import jakarta.validation.constraints.NotEmpty;
import tr.edu.etu.endofday.user.ProfileRules;
import tr.edu.etu.endofday.user.ValidPassword;

/** Body of PUT /me/password. Passwords are taken as they are, without trimming. */
public record ChangePasswordRequest(
        @NotEmpty(message = ProfileRules.PASSWORD_REQUIRED) String currentPassword,
        @ValidPassword String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[]";
    }
}
