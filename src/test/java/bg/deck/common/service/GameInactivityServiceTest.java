package bg.deck.common.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.Game;
import bg.deck.common.model.TurnClock;
import bg.deck.tabla.service.TablaUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The santase and tabla turn timer, against a real scheduler.
 *
 * <p>The case that mattered: a client sent its timeout twice at once, the
 * player then pressed "Continue", and was surrendered anyway at the deadline
 * "Continue" had moved — by a timer the second re-arm had orphaned.
 */
@DisplayName("GameInactivityService")
class GameInactivityServiceTest {

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final GameUtilService games = mock(GameUtilService.class);
    private final TablaUtilService tabla = mock(TablaUtilService.class);
    private final GameInactivityService timers =
            new GameInactivityService(scheduler, virtualThreads, games, tabla);

    private final UUID gameId = UUID.randomUUID();
    private final AtomicReference<Instant> deadline = new AtomicReference<>();
    private Game game;

    @BeforeEach
    void setUp() {
        TurnClock clock = mock(TurnClock.class);
        when(clock.getNextMoveTime()).thenAnswer(_ -> deadline.get());

        game = mock(Game.class);
        when(game.getId()).thenReturn(gameId);
        when(game.getGameType()).thenReturn(GameType.SANTASE);
        when(game.getTurnClock()).thenReturn(clock);
        when(games.findGameById(gameId)).thenReturn(Optional.of(game));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        virtualThreads.shutdownNow();
    }

    @Test
    @DisplayName("a timeout reported twice at once, then Continue: nobody is surrendered at the old deadline")
    void continueOutlivesADoubleReport() throws Exception {
        deadline.set(Instant.now().plusMillis(300));

        // The two reports, re-arming the same game at the same moment.
        CountDownLatch start = new CountDownLatch(1);
        Thread first = Thread.ofVirtual().start(() -> armWhen(start));
        Thread second = Thread.ofVirtual().start(() -> armWhen(start));
        start.countDown();
        first.join();
        second.join();

        // Continue: a fresh deadline, saved, and the timer set to it.
        deadline.set(Instant.now().plusSeconds(30));
        timers.updateNextMoveTime(game);

        verify(games, after(800).never()).surrenderByInactivity(gameId);
    }

    @Test
    @DisplayName("a turn that has run out is still surrendered")
    void aTurnThatRanOutIsSurrendered() {
        deadline.set(Instant.now().minusSeconds(1));

        timers.updateNextMoveTime(game);

        verify(games, timeout(2000)).surrenderByInactivity(gameId);
    }

    private void armWhen(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        timers.updateNextMoveTime(game);
    }
}
