package tr.edu.etu.endofday.order.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tr.edu.etu.endofday.common.TextInputs;

/** Body of POST /orders/{id}/cancel. */
public record CancelOrderRequest(
        @NotBlank(message = "İstek numarası zorunludur.")
        @Size(max = 64, message = "İstek numarası en fazla 64 karakter olabilir.")
        String requestId,

        Boolean simulateRefundFailure) {

    public CancelOrderRequest {
        requestId = TextInputs.trim(requestId);
    }

    boolean refundFails() {
        return Boolean.TRUE.equals(simulateRefundFailure);
    }
}
