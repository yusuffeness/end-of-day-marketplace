package tr.edu.etu.endofday.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import tr.edu.etu.endofday.common.TextInputs;
import tr.edu.etu.endofday.order.service.OrderService;

/** Body of POST /orders. Prices are never taken from the client. */
public record PlaceOrderRequest(
        @NotNull(message = "İşletme zorunludur.") @Positive(message = "Geçerli bir işletme seçiniz.")
        Long businessId,

        @NotEmpty(message = "Sipariş en az bir kalem içermelidir.")
        @Size(max = 50, message = "Sipariş en fazla 50 kalem içerebilir.")
        List<@Valid @NotNull(message = "Sipariş kalemi boş olamaz.") Item> items,

        @NotBlank(message = "İstek numarası zorunludur.")
        @Size(max = 64, message = "İstek numarası en fazla 64 karakter olabilir.")
        String requestId,

        Boolean simulatePaymentFailure) {

    public PlaceOrderRequest {
        requestId = TextInputs.trim(requestId);
    }

    OrderService.PlaceOrder toCommand() {
        return new OrderService.PlaceOrder(
                businessId,
                items.stream()
                        .map(item -> new OrderService.PlaceOrder.Item(item.listingId(), item.quantity()))
                        .toList(),
                requestId,
                Boolean.TRUE.equals(simulatePaymentFailure));
    }

    /** One listing and its quantity; the same listing may appear more than once and is merged into one item. */
    public record Item(
            @NotNull(message = "İlan zorunludur.") @Positive(message = "Geçerli bir ilan seçiniz.")
            Long listingId,

            @NotNull(message = "Adet zorunludur.")
            @Min(value = 1, message = "Adet en az 1 olmalıdır.")
            @Max(value = 100, message = "Adet en fazla 100 olabilir.")
            Integer quantity) {}
}
