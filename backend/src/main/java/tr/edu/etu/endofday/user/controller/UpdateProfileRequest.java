package tr.edu.etu.endofday.user.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.user.ProfileRules;

/** Body of PUT /me. Values are trimmed before validation; an empty phone number becomes {@code null}. */
public record UpdateProfileRequest(
        @NotBlank(message = ProfileRules.FIRST_NAME_REQUIRED)
        @Size(max = ProfileRules.NAME_MAX_LENGTH, message = ProfileRules.FIRST_NAME_TOO_LONG)
        String firstName,

        @NotBlank(message = ProfileRules.LAST_NAME_REQUIRED)
        @Size(max = ProfileRules.NAME_MAX_LENGTH, message = ProfileRules.LAST_NAME_TOO_LONG)
        String lastName,

        @Pattern(regexp = ProfileRules.PHONE_PATTERN, message = ProfileRules.PHONE_INVALID)
        String phone) {

    public UpdateProfileRequest {
        firstName = TextInputs.trim(firstName);
        lastName = TextInputs.trim(lastName);
        phone = TextInputs.trimToNull(phone);
    }

    @Override
    public String toString() {
        return "UpdateProfileRequest[]";
    }
}
