package bg.deck.tabla.service;

import bg.deck.tabla.model.TablaGame;
import bg.deck.tabla.model.TablaGameState;
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
 * Табла's turn clock. Running out is not always losing here: a roll with no
 * legal move is passed, and the opening has rules of its own.
 *
 * <p>The timing itself is {@link DeadlineTimer}'s; what running out means is
 * табла's, and it is here.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class TablaTurnTimer {

    private final DeadlineTimer deadlineTimer;
    private final TablaUtilService tablaUtilService;

    public void update(TablaGame game) {
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
     * A timer has fired: act only if the turn has really run out — the
     * deadline is read again, since "Continue" may have moved it.
     */
    private void runOut(UUID gameId) {
        Optional<TablaGame> game = tablaUtilService.findGameById(gameId);
        if (game.isEmpty() || game.get().getWinner() != null) {
            return;
        }
        if (!hasRunOut(game.get())) {
            update(game.get());
            return;
        }
        // Nobody has started yet: the opening has rules of its own for a die
        // left unthrown — but never throws it for anyone.
        if (tablaUtilService.openingTimedOut(gameId)) {
            tablaUtilService.findGameById(gameId).ifPresent(this::update);
            return;
        }
        // A roll with no legal move is passed, not lost: the player had nothing
        // to play, so the clock running out says nothing about them. The turn
        // goes to the opponent, and the timer is set again to the deadline that
        // hand-off just set.
        if (tablaUtilService.passIfBlocked(gameId)) {
            tablaUtilService.findGameById(gameId).ifPresent(this::update);
            return;
        }
        tablaUtilService.surrenderByInactivity(gameId);
    }

    /**
     * Re-arms every live game after a restart. A turn whose deadline passed
     * while the server was down is given a fresh one rather than lost on the
     * spot: the time went to the server, not to the player.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rescheduleActiveGames() {
        List<TablaGame> active = tablaUtilService.findAllActiveGames();
        active.forEach(this::rearmAfterRestart);
        if (!active.isEmpty()) {
            log.info("Re-armed табла turn timers for {} live game(s) after startup", active.size());
        }
    }

    private void rearmAfterRestart(TablaGame game) {
        try {
            if (hasRunOut(game) && game.getTurnClock() != null) {
                game.getTurnClock().extendNextMoveTime();
                tablaUtilService.saveGame(game);
                tablaUtilService.pushToBoth(game);
            }
            update(game);
        } catch (RuntimeException ex) {
            log.error("Could not re-arm табла game {} after startup", game.getId(), ex);
        }
    }

    private static Instant deadlineOf(TablaGame game) {
        TurnClock clock = game.getTurnClock();
        return clock == null || clock.getNextMoveTime() == null
                ? Instant.now().plusSeconds(clock == null ? TablaGameState.TURN_SECONDS : clock.turnSeconds())
                : clock.getNextMoveTime();
    }

    private static boolean hasRunOut(TablaGame game) {
        TurnClock clock = game.getTurnClock();
        return clock == null || clock.getNextMoveTime() == null
                || !Instant.now().isBefore(clock.getNextMoveTime());
    }
}
