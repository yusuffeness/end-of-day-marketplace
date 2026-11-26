package tr.edu.etu.endofday.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.user.EmailAddresses;
import tr.edu.etu.endofday.user.ProfileRules;
import tr.edu.etu.endofday.user.ValidPassword;

/**
 * Body of POST /auth/register. Names and phone number are trimmed and the email address is normalized before
 * validation; the password is taken as it is.
 */
public record RegisterRequest(
        @NotBlank(message = ProfileRules.FIRST_NAME_REQUIRED)
        @Size(max = ProfileRules.NAME_MAX_LENGTH, message = ProfileRules.FIRST_NAME_TOO_LONG)
        String firstName,

        @NotBlank(message = ProfileRules.LAST_NAME_REQUIRED)
        @Size(max = ProfileRules.NAME_MAX_LENGTH, message = ProfileRules.LAST_NAME_TOO_LONG)
        String lastName,

        @NotBlank(message = ProfileRules.EMAIL_REQUIRED)
        @Email(regexp = ProfileRules.EMAIL_PATTERN, message = ProfileRules.EMAIL_INVALID)
        @Size(max = ProfileRules.EMAIL_MAX_LENGTH, message = ProfileRules.EMAIL_TOO_LONG)
        String email,

        @Pattern(regexp = ProfileRules.PHONE_PATTERN, message = ProfileRules.PHONE_INVALID)
        String phone,

        @ValidPassword String password) {

    public RegisterRequest {
        firstName = TextInputs.trim(firstName);
        lastName = TextInputs.trim(lastName);
        email = EmailAddresses.normalize(email);
        phone = TextInputs.trimToNull(phone);
    }

    @Override
    public String toString() {
        return "RegisterRequest[]";
    }
}
