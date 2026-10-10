package bg.deck.common.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One timer per game, set to the deadline a turn runs out at.
 *
 * <p>Only the timing: when the deadline passes, the game's own turn timer is
 * called back with the game's id and decides what that means. It knows no
 * game, so santase and табла each have a turn timer of their own built on it,
 * and neither is a branch in here.
 */
@RequiredArgsConstructor
@Component
public class DeadlineTimer {

    /**
     * How long after the deadline a timer fires. A turn has run out only once
     * the clock is past its deadline, and the scheduler's sense of time and the
     * wall clock's can differ by a few milliseconds.
     */
    private static final Duration PAST_DEADLINE = Duration.ofMillis(100);

    private final ScheduledExecutorService scheduler;
    private final ExecutorService virtualThreadExecutor;

    private final Map<UUID, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /**
     * Sets this game's timer to the deadline, replacing whatever it was set to.
     *
     * <p>One step, not a remove and then a put. Two requests re-arming the same
     * game at once — a client that sent its timeout twice — each took the old
     * timer out and each put a new one in, so one of the new ones was left
     * running with nothing holding it. "Continue" then cancelled the other, and
     * the orphan surrendered the player at the old deadline.
     */
    public void arm(UUID gameId, Instant deadline, Consumer<UUID> onDeadline) {
        // Milliseconds, not whole seconds: rounding down fired a timer up to a
        // second before the deadline it was set for.
        long delay = Math.max(0, Duration.between(Instant.now(), deadline).plus(PAST_DEADLINE).toMillis());
        tasks.compute(gameId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return scheduler.schedule(
                    () -> virtualThreadExecutor.submit(() -> onDeadline.accept(gameId)),
                    delay, TimeUnit.MILLISECONDS);
        });
    }

    public void cancel(UUID gameId) {
        ScheduledFuture<?> timer = tasks.remove(gameId);
        if (timer != null) {
            timer.cancel(false);
        }
    }
}
