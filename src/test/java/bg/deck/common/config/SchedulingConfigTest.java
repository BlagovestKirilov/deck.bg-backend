package bg.deck.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduling setup: the timings a deployment can change, and the pool the
 * jobs actually run on.
 */
@DisplayName("How the scheduled jobs are set up")
class SchedulingConfigTest {

    @Nested
    @DisplayName("The timings")
    class Timings {

        @Test
        @DisplayName("a profile that says nothing still gets sensible ones")
        void defaultsStandOnTheirOwn() {
            SchedulingProperties properties = bind(Map.of());

            assertEquals(2, properties.poolSize());
            assertEquals(Duration.ofSeconds(30), properties.shutdownGrace());
            assertEquals(Duration.ofMinutes(5), properties.expiredLinks().interval());
            assertEquals(Duration.ofMinutes(1), properties.expiredLinks().initialDelay());
        }

        @Test
        @DisplayName("and a deployment can change any of them without a build")
        void everyTimingCanBeOverridden() {
            SchedulingProperties properties = bind(Map.of(
                    "deck.scheduling.pool-size", "4",
                    "deck.scheduling.shutdown-grace", "PT10S",
                    "deck.scheduling.expired-links.interval", "PT30S",
                    "deck.scheduling.expired-links.initial-delay", "PT5S"
            ));

            assertEquals(4, properties.poolSize());
            assertEquals(Duration.ofSeconds(10), properties.shutdownGrace());
            assertEquals(Duration.ofSeconds(30), properties.expiredLinks().interval());
            assertEquals(Duration.ofSeconds(5), properties.expiredLinks().initialDelay());
        }

        @Test
        @DisplayName("one timing named on its own leaves the rest at their defaults")
        void partialConfigurationKeepsTheRest() {
            SchedulingProperties properties = bind(Map.of("deck.scheduling.expired-links.interval", "PT2M"));

            assertEquals(Duration.ofMinutes(2), properties.expiredLinks().interval());
            assertEquals(Duration.ofMinutes(1), properties.expiredLinks().initialDelay());
            assertEquals(2, properties.poolSize());
        }

        private static SchedulingProperties bind(Map<String, String> configuration) {
            return new Binder(new MapConfigurationPropertySource(configuration))
                    .bindOrCreate("deck.scheduling", SchedulingProperties.class);
        }
    }

    @Nested
    @DisplayName("The pool")
    class Pool {

        private final SchedulingProperties properties =
                new Binder(new MapConfigurationPropertySource(Map.of()))
                        .bindOrCreate("deck.scheduling", SchedulingProperties.class);

        @Test
        @DisplayName("runs jobs on threads that say where they came from")
        void jobsRunOnNamedThreads() throws Exception {
            ThreadPoolTaskScheduler scheduler = scheduler();
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            scheduler.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                done.countDown();
            });

            assertTrue(done.await(3, TimeUnit.SECONDS), "the job ran");
            assertTrue(threadName.get().startsWith("deck-scheduler-"),
                    "a thread dump names the job's home, but this one said " + threadName.get());
            // The configured size, not getPoolSize(): that counts the threads
            // started so far, which after one job is one.
            assertEquals(2, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize(),
                    "two threads, so a long job cannot hold up an unrelated one");
            scheduler.shutdown();
        }

        @Test
        @DisplayName("a job that throws is caught, and the next one still runs")
        void oneFailureDoesNotStopTheRest() throws Exception {
            ThreadPoolTaskScheduler scheduler = scheduler();
            CountDownLatch after = new CountDownLatch(1);

            scheduler.schedule(() -> {
                throw new IllegalStateException("the database was away");
            }, Instant.now());
            scheduler.schedule(after::countDown, Instant.now().plusMillis(50));

            assertTrue(after.await(3, TimeUnit.SECONDS), "the schedule carried on past the failure");
            scheduler.shutdown();
        }

        /** Spring calls initialize() on the bean; standing alone, the test does. */
        private ThreadPoolTaskScheduler scheduler() {
            ThreadPoolTaskScheduler scheduler =
                    (ThreadPoolTaskScheduler) new SchedulingConfig().taskScheduler(properties);
            scheduler.initialize();
            return scheduler;
        }
    }
}
