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
 * @param trickPause  how long a finished trick stays on the table before the
 *                    next may be led — the time the table takes to show it
 *                    being swept to whoever took it
 * @param cutTimeout  how long the player cutting may take before the deck is
 *                    cut for them
 * @param dealPause   how long after the cut before the bidding clock starts —
 *                    the time the table takes to show the deal
 * @param handPause   how long after a hand is counted before the cut clock
 *                    starts — the last trick being taken and the count shown
 */
@ConfigurationProperties(prefix = "deck.belot")
public record BelotProperties(
        @DefaultValue("PT30S") Duration turnTimeout,
        @DefaultValue("PT1.6S") Duration trickPause,
        @DefaultValue("PT8S") Duration cutTimeout,
        @DefaultValue("PT3S") Duration dealPause,
        @DefaultValue("PT11S") Duration handPause
) {
}
