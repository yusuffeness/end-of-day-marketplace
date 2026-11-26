package tr.edu.etu.endofday.review.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tr.edu.etu.endofday.common.TextInputs;

/** Body of POST /orders/{id}/review. An empty comment is stored as {@code null}. */
public record ReviewRequest(
        @NotNull(message = "Puan zorunludur.")
        @Min(value = 1, message = "Puan 1 ile 5 arasında olmalıdır.")
        @Max(value = 5, message = "Puan 1 ile 5 arasında olmalıdır.")
        Integer rating,

        @Size(max = 1000, message = "Yorum en fazla 1000 karakter olabilir.")
        String comment) {

    public ReviewRequest {
        comment = TextInputs.trimToNull(comment);
    }
}
