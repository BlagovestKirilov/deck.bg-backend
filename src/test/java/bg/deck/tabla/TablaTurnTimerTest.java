package bg.deck.tabla;

import bg.deck.tabla.model.TablaGame;
import bg.deck.common.model.TurnClock;
import bg.deck.common.scheduler.DeadlineTimer;
import bg.deck.tabla.model.TablaGameState;
import bg.deck.tabla.service.TablaTurnTimer;
import bg.deck.tabla.service.TablaUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Табла's turn timer: running out is not always losing. */
@DisplayName("Табла's turn timer")
class TablaTurnTimerTest {

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final TablaUtilService tabla = mock(TablaUtilService.class);
    private final TablaTurnTimer timers =
            new TablaTurnTimer(new DeadlineTimer(scheduler, virtualThreads), tabla);

    private final UUID gameId = UUID.randomUUID();
    private final AtomicReference<Instant> deadline = new AtomicReference<>(Instant.now().minusSeconds(1));
    private TablaGame game;

    @BeforeEach
    void setUp() {
        TurnClock clock = mock(TurnClock.class);
        when(clock.getNextMoveTime()).thenAnswer(_ -> deadline.get());
        game = mock(TablaGame.class);
        when(game.getId()).thenReturn(gameId);
        when(game.getTurnClock()).thenReturn(clock);
        when(tabla.findGameById(gameId)).thenReturn(Optional.of(game));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        virtualThreads.shutdownNow();
    }

    @Test
    @DisplayName("a roll with no legal move is passed, and the player is not surrendered")
    void aBlockedRollIsPassed() {
        // A pass hands the turn over, and with it a fresh deadline.
        when(tabla.passIfBlocked(gameId)).thenAnswer(_ -> {
            deadline.set(Instant.now().plusSeconds(TablaGameState.TURN_SECONDS));
            return true;
        });

        timers.update(game);

        verify(tabla, timeout(2000)).passIfBlocked(gameId);
        verify(tabla, after(300).never()).surrenderByInactivity(gameId);
    }

    @Test
    @DisplayName("a turn that has run out with a move to make is surrendered")
    void aTurnThatRanOutIsSurrendered() {
        timers.update(game);

        verify(tabla, timeout(2000)).surrenderByInactivity(gameId);
    }
}
