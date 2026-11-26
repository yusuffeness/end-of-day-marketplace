package tr.edu.etu.endofday.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import tr.edu.etu.endofday.common.time.ClockConfig;

/** A clock in the application's time zone that tests set and move by hand, so that time rules are deterministic. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant initial, ZoneId zone) {
        this.instant = new AtomicReference<>(initial);
        this.zone = zone;
    }

    public MutableClock(Instant initial) {
        this(initial, ClockConfig.ZONE);
    }

    /** Sets the clock to a local date-time in the application's time zone. */
    public void setTo(LocalDateTime localDateTime) {
        instant.set(localDateTime.atZone(zone).toInstant());
    }

    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant.get(), newZone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
