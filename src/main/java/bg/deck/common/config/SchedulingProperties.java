package bg.deck.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Everything about the scheduled jobs that is worth changing without a build.
 *
 * <p>Defaults live here rather than only in {@code application.yml}, so a
 * profile that says nothing still starts with sensible timings.
 *
 * @param poolSize      threads kept for scheduled work
 * @param shutdownGrace how long a shutdown waits for a job to finish
 * @param expiredLinks  when the job that retires links nobody opened runs
 */
@ConfigurationProperties(prefix = "deck.scheduling")
public record SchedulingProperties(
        @DefaultValue("2") int poolSize,
        @DefaultValue("PT30S") Duration shutdownGrace,
        @DefaultValue JobSchedule expiredLinks
) {
}
