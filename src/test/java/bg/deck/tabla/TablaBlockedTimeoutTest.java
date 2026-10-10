package bg.deck.tabla;

import bg.deck.tabla.model.TablaGame;
import bg.deck.tabla.model.TablaSeat;
import bg.deck.tabla.model.TablaGameState;
import bg.deck.tabla.repository.TablaGameRepository;
import bg.deck.tabla.service.TablaDiceService;
import bg.deck.tabla.service.TablaStatsService;
import bg.deck.tabla.service.TablaUtilService;
import bg.deck.common.service.WebSocketService;
import bg.deck.tabla.engine.BoardState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What happens when the clock runs out on a табла roll that has no legal move.
 *
 * <p>The player had nothing to play — the dice decided the turn, not them — so
 * the turn is passed rather than the game lost. Their own client passes for
 * them a moment after the roll, but only while it is open; closing the app on
 * a blocked roll used to cost the whole game.
 */
@DisplayName("A blocked roll that times out")
class TablaBlockedTimeoutTest {

    private TablaGameRepository tablaGameRepository;
    private TablaUtilService tablaUtilService;

    private TablaGame game;
    private TablaSeat white;
    private TablaSeat black;
    private TablaGameState state;

    @BeforeEach
    void setUp() {
        tablaGameRepository = mock(TablaGameRepository.class);
        tablaUtilService = new TablaUtilService(
                tablaGameRepository,
                mock(WebSocketService.class),
                mock(TablaStatsService.class),
                mock(TablaDiceService.class));

        // A player's name comes from the account behind the seat.
        white = seat("petko91");
        black = seat("ninja2011");

        state = new TablaGameState();
        state.setBoardState(BoardState.initial());
        state.setInTurnPlayer(white);

        game = new TablaGame();
        game.setFirstPlayer(white);
        game.setSecondPlayer(black);
        game.setState(state);
        // The push reads the id; it is generated on persist, which never
        // happens here.
        setId(game, UUID.randomUUID());

        when(tablaGameRepository.findById(any())).thenReturn(Optional.of(game));
        when(tablaGameRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void setId(TablaGame game, UUID id) {
        try {
            var field = Class.forName("bg.deck.common.model.base.BaseEntity").getDeclaredField("id");
            field.setAccessible(true);
            field.set(game, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static TablaSeat seat(String username) {
        TablaSeat player = new TablaSeat();
        player.setUsername(username);
        return player;
    }

    @Test
    void passesTheTurnAndLeavesTheGameRunning() {
        state.setDie1(6);
        state.setDie2(5);
        state.setMaxDiceUsable(0);

        assertTrue(tablaUtilService.passIfBlocked(UUID.randomUUID()));

        assertEquals(black, state.getInTurnPlayer(), "the opponent plays next");
        assertNull(game.getWinner(), "nobody wins a turn that could not be played");
        assertNull(state.getDie1(), "the roll is cleared with the turn");
    }

    @Test
    void aTurnWithMovesLeftIsNotPassed() {
        state.setDie1(6);
        state.setDie2(5);
        state.setMaxDiceUsable(2);

        assertFalse(tablaUtilService.passIfBlocked(UUID.randomUUID()));
        assertEquals(white, state.getInTurnPlayer(), "their turn, their clock");
    }

    @Test
    void aTurnBeforeTheRollIsNotPassed() {
        state.setMaxDiceUsable(0);

        assertFalse(tablaUtilService.passIfBlocked(UUID.randomUUID()),
                "no dice yet — running out of time here is genuinely not moving");
        assertEquals(white, state.getInTurnPlayer());
    }

    @Test
    void aFinishedGameIsLeftAlone() {
        state.setDie1(6);
        state.setDie2(5);
        state.setMaxDiceUsable(0);
        // The plain setter: the two-argument one also moves the players' stats,
        // which is not what this is about.
        game.setWinner(black);

        assertFalse(tablaUtilService.passIfBlocked(UUID.randomUUID()));
        assertEquals(white, state.getInTurnPlayer(), "a finished game keeps its last state");
    }
}
