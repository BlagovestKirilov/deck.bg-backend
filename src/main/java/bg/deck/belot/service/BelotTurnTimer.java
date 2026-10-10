package bg.deck.belot.service;

import bg.deck.belot.model.event.BelotTurnClock;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Acts for a seat the moment its time runs out.
 *
 * <p>One timer per table, set to the deadline the table has just been sent,
 * rather than a sweep asking the database every second whether anybody is
 * late. Nothing runs while nobody is playing, and the absent seat is acted for
 * at its deadline rather than up to a sweep later.
 *
 * <p>The scheduler's one thread only keeps time. What a timer does — read the
 * deal, act, tell the table — runs on a virtual thread of its own, so a slow
 * query at one table never holds up the clock of another, and ten tables
 * running out in the same second are acted for at once.
 *
 * <p>Timers live in this JVM, so they are set again for every live table at
 * startup. Two instances overlapping during a deploy may both fire for the
 * same turn; the second finds it taken, exactly as when two screens report
 * the same clock reaching nought.
 *
 * @see BelotService#timeUp(String) for the screens' own report, which stays
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotTurnTimer {

    /**
     * How long after the deadline a timer fires. A turn has run out only once
     * the clock is past its deadline, not at it, and the scheduler's sense of
     * time and the wall clock's can differ by a few milliseconds.
     */
    private static final Duration PAST_DEADLINE = Duration.ofMillis(100);

    private final ScheduledExecutorService scheduler;
    private final ExecutorService virtualThreadExecutor;
    private final BelotService belotService;

    private final Map<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    /**
     * Sets the table's timer to where its clock now stands, once the change
     * that moved it has committed — so a move that rolls back leaves no timer
     * behind.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClockSet(BelotTurnClock clock) {
        set(clock);
    }

    /** Every live table, again, after a restart: the timers did not survive it. */
    @EventListener(ApplicationReadyEvent.class)
    public void setAllAfterStartup() {
        List<BelotTurnClock> live = belotService.liveClocks();
        live.forEach(this::set);
        if (!live.isEmpty()) {
            log.info("Belot: set the turn clock again at {} table(s) after startup", live.size());
        }
    }

    @PreDestroy
    public void cancelAll() {
        timers.values().forEach(timer -> timer.cancel(false));
        timers.clear();
    }

    private void set(BelotTurnClock clock) {
        ScheduledFuture<?> previous = timers.remove(clock.tableId());
        if (previous != null) {
            previous.cancel(false);
        }
        if (!clock.isRunning()) {
            return;
        }

        long delay = Math.max(0, Duration.between(Instant.now(), clock.deadline()).plus(PAST_DEADLINE).toMillis());
        timers.put(clock.tableId(), scheduler.schedule(
                () -> virtualThreadExecutor.execute(() -> runOut(clock)),
                delay, TimeUnit.MILLISECONDS));
    }

    /**
     * The clock has reached its deadline: act for the seat.
     *
     * <p>When acting moved the table on, telling the table set the next timer
     * already. When it did not — the turn had been played a moment before, or
     * this fired early — the deal is read again and its clock set to wherever
     * it now stands. A deadline already behind us with nothing to act on is
     * left alone rather than set again, which would only fire again at once.
     */
    private void runOut(BelotTurnClock clock) {
        try {
            if (belotService.actFor(clock.dealId())) {
                return;
            }
        } catch (DataIntegrityViolationException alreadyTaken) {
            // A screen reported the same deadline and acted first: the unique
            // place of every bid and card refused this second go. Ordinary.
            log.debug("Belot: the turn at table {} was already taken", clock.tableId());
        } catch (RuntimeException e) {
            // Somebody moved at the same moment, or something worse. Either
            // way the clock is read again below.
            log.warn("Belot: could not act for the absent seat at table {}", clock.tableId(), e);
        }

        belotService.clockOf(clock.dealId())
                .filter(current -> current.deadline().isAfter(Instant.now()))
                .ifPresent(this::set);
    }
}
