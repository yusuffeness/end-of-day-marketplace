package tr.edu.etu.endofday.auth.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import tr.edu.etu.endofday.user.EmailAddresses;
import tr.edu.etu.endofday.user.ProfileRules;

/** Body of POST /auth/login. The email address is normalized; the password is taken as it is. */
public record LoginRequest(
        @NotBlank(message = ProfileRules.EMAIL_REQUIRED) String email,
        @NotEmpty(message = ProfileRules.PASSWORD_REQUIRED) String password) {

    public LoginRequest {
        email = EmailAddresses.normalize(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[]";
    }
}
