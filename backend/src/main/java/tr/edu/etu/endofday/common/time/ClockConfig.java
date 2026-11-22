package tr.edu.etu.endofday.common.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the single time source that all components must use instead of calling now() directly. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
