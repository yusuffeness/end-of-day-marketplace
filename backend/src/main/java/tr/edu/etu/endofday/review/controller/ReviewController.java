package tr.edu.etu.endofday.review.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.review.service.ReviewService;

/** The customer's review of a delivered order. */
@RestController
@PreAuthorize("hasRole('MUSTERI')")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/v1/orders/{orderId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse review(
            @PathVariable long orderId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ReviewRequest request) {
        ReviewService.Review review = reviewService.review(user.id(), orderId, request.rating(), request.comment());
        return new ReviewResponse(review.orderId(), review.rating(), review.comment(), review.createdAt());
    }

    /** The stored review. */
    public record ReviewResponse(long orderId, int rating, String comment, LocalDateTime createdAt) {}
}
