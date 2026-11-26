package tr.edu.etu.endofday.business.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.user.ProfileRules;

/** Body of PUT /businesses/{id}: the type, approval and active flag cannot be changed here. */
public record UpdateBusinessRequest(
        @NotBlank(message = BusinessRules.NAME_REQUIRED) @Size(max = 100, message = BusinessRules.NAME_TOO_LONG)
        String name,

        @NotBlank(message = BusinessRules.CITY_REQUIRED) @Size(max = 50, message = BusinessRules.CITY_TOO_LONG)
        String city,

        @NotBlank(message = BusinessRules.DISTRICT_REQUIRED) @Size(max = 50, message = BusinessRules.DISTRICT_TOO_LONG)
        String district,

        @NotBlank(message = BusinessRules.ADDRESS_REQUIRED) @Size(max = 255, message = BusinessRules.ADDRESS_TOO_LONG)
        String address,

        @NotBlank(message = BusinessRules.PHONE_REQUIRED)
        @Pattern(regexp = ProfileRules.PHONE_PATTERN, message = ProfileRules.PHONE_INVALID)
        String phone) {

    public UpdateBusinessRequest {
        name = TextInputs.trim(name);
        city = TextInputs.trim(city);
        district = TextInputs.trim(district);
        address = TextInputs.trim(address);
        phone = TextInputs.trim(phone);
    }
}
