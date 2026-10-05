package bg.deck.common.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.Game;
import bg.deck.common.model.TurnClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import bg.deck.tabla.service.TablaUtilService;

/**
 * Fires the inactivity surrender when a player runs out of time.
 *
 * <p>Works for both games: it reads the deadline through {@link TurnClock} and
 * dispatches the timeout to the service owning that game type.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class GameInactivityService {

    private final ScheduledExecutorService scheduler;
    private final ExecutorService virtualThreadExecutor;
    private final GameUtilService gameUtilService;
    private final TablaUtilService tablaUtilService;

    /**
     * How long after the deadline a timer fires. A turn has run out only once
     * the clock is past its deadline, and the scheduler's sense of time and the
     * wall clock's can differ by a few milliseconds.
     */
    private static final Duration PAST_DEADLINE = Duration.ofMillis(100);

    private final Map<UUID, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public void updateNextMoveTime(Game game) {
        if (game.getWinner() != null) {
            cancel(game.getId());
            return;
        }

        // Schedule to the persisted deadline rather than a hardcoded 33s. That
        // literal used to live in two places and stayed in sync only because
        // every mutation path happened to touch both.
        TurnClock clock = game.getTurnClock();
        Instant deadline = clock == null || clock.getNextMoveTime() == null
                ? Instant.now().plusSeconds(clock == null ? TurnClock.TURN_SECONDS : clock.turnSeconds())
                : clock.getNextMoveTime();
        // Milliseconds, not whole seconds: rounding down fired a timer up to a
        // second before the deadline it was set for.
        long delay = Math.max(0, Duration.between(Instant.now(), deadline).plus(PAST_DEADLINE).toMillis());

        UUID gameId = game.getId();
        GameType type = game.getGameType();

        // One step, not a remove and then a put. Two requests re-arming the
        // same game at once — a client that sent its timeout twice — each took
        // the old timer out and each put a new one in, so one of the new ones
        // was left running with nothing holding it. "Continue" then cancelled
        // the other, and the orphan surrendered the player at the old deadline.
        tasks.compute(gameId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return scheduler.schedule(
                    () -> virtualThreadExecutor.submit(() -> runOut(gameId, type)),
                    delay, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * A timer has fired: act only if the turn has really run out.
     *
     * <p>The deadline is read again from the database. A player who pressed
     * "Continue" has a later one than the timer was set for, and losing the
     * game to a timer that should no longer exist is the one outcome that
     * cannot be undone — so a turn that has not run out is set again to its
     * real deadline instead.
     */
    private void runOut(UUID gameId, GameType gameType) {
        Optional<Game> game = gameUtilService.findGameById(gameId);
        if (game.isEmpty() || game.get().getWinner() != null) {
            return;
        }
        if (!hasRunOut(game.get())) {
            updateNextMoveTime(game.get());
            return;
        }
        surrender(gameId, gameType);
    }

    private static boolean hasRunOut(Game game) {
        TurnClock clock = game.getTurnClock();
        return clock == null || clock.getNextMoveTime() == null
                || !Instant.now().isBefore(clock.getNextMoveTime());
    }

    private void surrender(UUID gameId, GameType gameType) {
        if (gameType == GameType.TABLA) {
            // A табла roll with no legal move is passed, not lost: the player
            // had nothing to play, so the clock running out says nothing about
            // them. The turn goes to the opponent, and the timer is re-armed
            // against the deadline that hand-off just set.
            // Nobody has started yet: the opening has rules of its own for a
            // die left unthrown — but never throws it for anyone.
            if (tablaUtilService.openingTimedOut(gameId)) {
                gameUtilService.findGameById(gameId).ifPresent(this::updateNextMoveTime);
                return;
            }
            if (tablaUtilService.passIfBlocked(gameId)) {
                gameUtilService.findGameById(gameId).ifPresent(this::updateNextMoveTime);
                return;
            }
            tablaUtilService.surrenderByInactivity(gameId);
        } else {
            gameUtilService.surrenderByInactivity(gameId);
        }
    }

    public void cancel(UUID gameId) {
        ScheduledFuture<?> f = tasks.remove(gameId);
        if (f != null) {
            f.cancel(false);
        }
    }

    /**
     * Re-arms every live game after a restart.
     *
     * <p>The task map lives only in this JVM, so without this a redeploy left
     * every in-progress game hanging until somebody manually surrendered.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rescheduleActiveGames() {
        List<Game> active = gameUtilService.findAllActiveGames();
        active.forEach(this::updateNextMoveTime);
        if (!active.isEmpty()) {
            log.info("Re-armed inactivity timers for {} live game(s) after startup", active.size());
        }
    }
}
