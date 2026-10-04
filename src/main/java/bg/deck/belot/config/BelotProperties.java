package bg.deck.belot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Belot's own timings, changeable without a build.
 *
 * <p>A record, because it only carries values, and registered by the
 * configuration that needs it — Spring cannot proxy a final class.
 *
 * @param turnTimeout how long a seat may think before the table acts for them
 * @param turnSweep   how often the tables are checked for a seat that has run out
 * @param sweepDelay  how long after startup the first check happens
 * @param trickPause  how long a finished trick stays on the table before the
 *                    next may be led — the time the table takes to show it
 *                    being swept to whoever took it
 */
@ConfigurationProperties(prefix = "deck.belot")
public record BelotProperties(
        @DefaultValue("PT30S") Duration turnTimeout,
        @DefaultValue("PT1S") Duration turnSweep,
        @DefaultValue("PT1M") Duration sweepDelay,
        @DefaultValue("PT1.6S") Duration trickPause
) {
}
