package bg.deck.common.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * When one scheduled job runs.
 *
 * <p>Bound as part of {@link SchedulingProperties}: a job named there gets
 * whichever of these two a deployment states, and these defaults for the rest.
 *
 * @param interval     the gap between the end of one run and the start of the
 *                     next — a delay, not a rate, so a slow run can never
 *                     overlap the one behind it
 * @param initialDelay how long after startup the first run happens, leaving the
 *                     application to finish coming up first
 */
public record JobSchedule(
        @DefaultValue("PT5M") Duration interval,
        @DefaultValue("PT1M") Duration initialDelay
) {
}
