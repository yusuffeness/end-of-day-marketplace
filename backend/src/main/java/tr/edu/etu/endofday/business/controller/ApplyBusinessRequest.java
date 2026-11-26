package tr.edu.etu.endofday.business.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tr.edu.etu.endofday.business.BusinessType;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.user.ProfileRules;

/** Body of POST /businesses. Texts are trimmed; the lengths follow the ISLETME columns. */
public record ApplyBusinessRequest(
        @NotBlank(message = BusinessRules.NAME_REQUIRED) @Size(max = 100, message = BusinessRules.NAME_TOO_LONG)
        String name,

        @NotNull(message = "İşletme türü zorunludur.") BusinessType type,

        @NotBlank(message = BusinessRules.CITY_REQUIRED) @Size(max = 50, message = BusinessRules.CITY_TOO_LONG)
        String city,

        @NotBlank(message = BusinessRules.DISTRICT_REQUIRED) @Size(max = 50, message = BusinessRules.DISTRICT_TOO_LONG)
        String district,

        @NotBlank(message = BusinessRules.ADDRESS_REQUIRED) @Size(max = 255, message = BusinessRules.ADDRESS_TOO_LONG)
        String address,

        @NotBlank(message = BusinessRules.PHONE_REQUIRED)
        @Pattern(regexp = ProfileRules.PHONE_PATTERN, message = ProfileRules.PHONE_INVALID)
        String phone) {

    public ApplyBusinessRequest {
        name = TextInputs.trim(name);
        city = TextInputs.trim(city);
        district = TextInputs.trim(district);
        address = TextInputs.trim(address);
        phone = TextInputs.trim(phone);
    }
}
