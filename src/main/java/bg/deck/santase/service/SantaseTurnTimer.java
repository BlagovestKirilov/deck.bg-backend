package bg.deck.santase.service;

import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseGameState;
import bg.deck.common.model.TurnClock;
import bg.deck.common.scheduler.DeadlineTimer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Santase's turn clock: a player whose time runs out loses the game.
 *
 * <p>Set to the deadline the game was just saved with, after every change that
 * moves it. The timing itself is {@link DeadlineTimer}'s; what running out
 * means is santase's, and it is here.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class SantaseTurnTimer {

    private final DeadlineTimer deadlineTimer;
    private final SantaseTableService santaseTableService;
    private final SantaseDealService santaseDealService;
    private final WebSocketUtilService webSocketUtilService;

    public void update(SantaseGame game) {
        if (game.getWinner() != null) {
            cancel(game.getId());
            return;
        }
        deadlineTimer.arm(game.getId(), deadlineOf(game), this::runOut);
    }

    public void cancel(UUID gameId) {
        deadlineTimer.cancel(gameId);
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
    private void runOut(UUID gameId) {
        Optional<SantaseGame> game = santaseTableService.findGameById(gameId);
        if (game.isEmpty() || game.get().getWinner() != null) {
            return;
        }
        if (!hasRunOut(game.get())) {
            update(game.get());
            return;
        }
        santaseDealService.surrenderByInactivity(gameId);
    }

    /**
     * Re-arms every live game after a restart.
     *
     * <p>The timers live only in this JVM, so without this a redeploy left every
     * game in progress hanging. A turn whose deadline passed while the server
     * was down is given a fresh one rather than lost on the spot: the time went
     * to the server, not to the player.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rescheduleActiveGames() {
        List<SantaseGame> active = santaseTableService.findAllActiveGames();
        active.forEach(this::rearmAfterRestart);
        if (!active.isEmpty()) {
            log.info("Re-armed santase turn timers for {} live game(s) after startup", active.size());
        }
    }

    private void rearmAfterRestart(SantaseGame game) {
        try {
            if (hasRunOut(game) && game.getTurnClock() != null) {
                game.getTurnClock().extendNextMoveTime();
                santaseTableService.saveGame(game);
                webSocketUtilService.updateGameState(game);
            }
            update(game);
        } catch (RuntimeException ex) {
            log.error("Could not re-arm santase game {} after startup", game.getId(), ex);
        }
    }

    private static Instant deadlineOf(SantaseGame game) {
        // Schedule to the persisted deadline rather than a hardcoded 33s. That
        // literal used to live in two places and stayed in sync only because
        // every mutation path happened to touch both.
        TurnClock clock = game.getTurnClock();
        return clock == null || clock.getNextMoveTime() == null
                ? Instant.now().plusSeconds(clock == null ? SantaseGameState.TURN_SECONDS : clock.turnSeconds())
                : clock.getNextMoveTime();
    }

    private static boolean hasRunOut(SantaseGame game) {
        TurnClock clock = game.getTurnClock();
        return clock == null || clock.getNextMoveTime() == null
                || !Instant.now().isBefore(clock.getNextMoveTime());
    }
}
