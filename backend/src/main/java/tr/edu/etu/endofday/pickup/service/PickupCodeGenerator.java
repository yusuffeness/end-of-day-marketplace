package tr.edu.etu.endofday.pickup.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Draws six-digit pickup codes ("000000" to "999999") that cannot be guessed from earlier codes. */
@Component
public class PickupCodeGenerator {

    private static final int CODE_COUNT = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        return "%06d".formatted(random.nextInt(CODE_COUNT));
    }
}
