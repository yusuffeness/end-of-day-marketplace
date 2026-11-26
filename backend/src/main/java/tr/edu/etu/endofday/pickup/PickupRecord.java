package tr.edu.etu.endofday.pickup;

import java.time.LocalDateTime;

/** A TESLIM_ALMA row with the business of its order. */
public record PickupRecord(
        long orderId, long businessId, LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime pickedUpAt) {

    /** Both ends of the window count as inside, matching the not-picked-up event (teslim_bitis < NOW()). */
    public boolean isWithinWindow(LocalDateTime now) {
        return !now.isBefore(windowStart) && !now.isAfter(windowEnd);
    }
}
