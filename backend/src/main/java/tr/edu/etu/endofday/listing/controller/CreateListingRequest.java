package tr.edu.etu.endofday.listing.controller;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.common.TextInputs;

/**
 * Body of POST /businesses/{id}/listings. Texts are trimmed and times are truncated to whole seconds, as stored in
 * DATETIME columns. Amounts are strings with up to two decimals.
 */
public record CreateListingRequest(
        @NotBlank(message = "Başlık zorunludur.") @Size(max = 100, message = "Başlık en fazla 100 karakter olabilir.")
        String title,

        @NotBlank(message = "Açıklama zorunludur.")
        @Size(max = 2000, message = "Açıklama en fazla 2000 karakter olabilir.")
        String description,

        @NotBlank(message = "Alerjen bilgisi zorunludur; alerjen yoksa bunu açıkça belirtiniz.")
        @Size(max = 500, message = "Alerjen bilgisi en fazla 500 karakter olabilir.")
        String allergenInfo,

        @NotNull(message = "Normal fiyat zorunludur.")
        @Pattern(regexp = Money.PATTERN, message = "Normal fiyat geçerli bir tutar olmalıdır, örneğin \"120.00\".")
        String normalPrice,

        @NotNull(message = "Satış fiyatı zorunludur.")
        @Pattern(regexp = Money.PATTERN, message = "Satış fiyatı geçerli bir tutar olmalıdır, örneğin \"60.00\".")
        String salePrice,

        @NotNull(message = "Toplam adet zorunludur.")
        @Min(value = 1, message = "Toplam adet en az 1 olmalıdır.")
        @Max(value = 10_000, message = "Toplam adet en fazla 10000 olabilir.")
        Integer totalQuantity,

        @NotNull(message = "Teslim başlangıcı zorunludur.") LocalDateTime pickupStart,

        @NotNull(message = "Teslim bitişi zorunludur.") LocalDateTime pickupEnd) {

    public CreateListingRequest {
        title = TextInputs.trim(title);
        description = TextInputs.trim(description);
        allergenInfo = TextInputs.trim(allergenInfo);
        pickupStart = pickupStart == null ? null : pickupStart.truncatedTo(ChronoUnit.SECONDS);
        pickupEnd = pickupEnd == null ? null : pickupEnd.truncatedTo(ChronoUnit.SECONDS);
    }

    /** The sale price must be greater than zero and lower than the normal price (CHECK on PAKET_ILANI). */
    @AssertTrue(message = "Satış fiyatı sıfırdan büyük ve normal fiyattan düşük olmalıdır.")
    public boolean isSalePriceDiscounted() {
        BigDecimal normal = parsed(normalPrice);
        BigDecimal sale = parsed(salePrice);
        return normal == null || sale == null || (sale.signum() > 0 && sale.compareTo(normal) < 0);
    }

    BigDecimal normalPriceAmount() {
        return Money.parse(normalPrice);
    }

    BigDecimal salePriceAmount() {
        return Money.parse(salePrice);
    }

    private static BigDecimal parsed(String value) {
        return value != null && value.matches(Money.PATTERN) ? Money.parse(value) : null;
    }
}
