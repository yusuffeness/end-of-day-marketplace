package tr.edu.etu.endofday.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void clockUsesTheIstanbulTimeZone() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }
}
