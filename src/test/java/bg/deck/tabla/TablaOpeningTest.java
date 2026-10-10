package bg.deck.tabla;

import bg.deck.common.model.Game;
import bg.deck.common.model.Player;
import bg.deck.tabla.model.TablaGameState;
import bg.deck.common.model.User;
import bg.deck.tabla.model.dto.OpeningThrowDTO;
import bg.deck.tabla.model.response.TablaStateResponse;
import bg.deck.common.service.GameUtilService;
import bg.deck.tabla.service.TablaDiceService;
import bg.deck.tabla.service.TablaStatsService;
import bg.deck.tabla.service.TablaUtilService;
import bg.deck.common.service.WebSocketService;
import bg.deck.tabla.engine.Dice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The opening roll: each player throws one die themselves, the higher starts,
 * and starts by playing those two dice. Equal dice are thrown again. Both
 * players see every die — their own and their opponent's — as it lands.
 */
@DisplayName("The opening roll")
class TablaOpeningTest {

    private static final UUID GAME_ID = UUID.fromString("7a3c2f10-5b6e-4d1a-9c8b-2e4f6a8d0b1c");

    private final TablaDiceService realDice = new TablaDiceService();

    private GameUtilService gameUtilService;
    private Player white;
    private Player black;

    @BeforeEach
    void setUp() {
        gameUtilService = mock(GameUtilService.class);
        // Persisting is what assigns the id; the dice are derived from it.
        when(gameUtilService.saveGame(any())).thenAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            if (game.getId() == null) setId(game, GAME_ID);
            return game;
        });

        white = seat("petko91");
        black = seat("ninja2011");
    }

    /* ---------------- the start ---------------- */

    @Test
    @DisplayName("a new game waits for both players to throw — nobody is on turn")
    void nobodyStartsUntilBothThrow() {
        Game game = start(seedWhere(false));
        TablaGameState state = game.getTablaState();
        TablaUtilService service = service();

        assertTrue(service.isOpening(game));
        assertNull(state.getInTurnPlayer());
        assertNull(state.getDie1());
        assertNull(state.getDie2());
        assertEquals(0, state.getTurnIndex());
        assertNotNull(state.getNextMoveTime(), "the opening window is running");

        TablaStateResponse view = service.buildState(game, "petko91");
        assertTrue(view.openingPhase());
        assertFalse(view.isOnTurn());
        assertNull(view.openingMine());
        assertNull(view.openingOpponent());
    }

    @Test
    @DisplayName("a throw shows its die to both players, and not in the table's dice row")
    void oneThrowIsSeenByBoth() {
        byte[] seed = seedWhere(false);
        Game game = start(seed);
        TablaUtilService service = service();
        Dice pair = realDice.roll(seed, GAME_ID, 0);

        assertTrue(service.openingThrow(game, white));

        TablaStateResponse mine = service.buildState(game, "petko91");
        TablaStateResponse theirs = service.buildState(game, "ninja2011");
        assertEquals(pair.d1(), mine.openingMine());
        assertNull(mine.openingOpponent(), "the other die is not thrown yet");
        assertEquals(pair.d1(), theirs.openingOpponent());
        assertNull(theirs.openingMine());

        // The opening die must not look like a roll.
        assertNull(mine.die1());
        assertNull(mine.die2());
        assertNull(theirs.die1());
        assertTrue(service.isOpening(game));
    }

    @Test
    @DisplayName("a second tap on your die changes nothing")
    void throwingTwiceIsIgnored() {
        Game game = start(seedWhere(false));
        TablaUtilService service = service();

        assertTrue(service.openingThrow(game, white));
        Integer thrown = game.getTablaState().getDie1();
        assertFalse(service.openingThrow(game, white));
        assertEquals(thrown, game.getTablaState().getDie1());
        assertNull(game.getTablaState().getDie2());
    }

    /* ---------------- settling it ---------------- */

    @Test
    @DisplayName("the higher die starts, playing both opening dice — in whichever order they were thrown")
    void theHigherDieStartsWithBothDice() {
        byte[] seed = seedWhere(false);
        Game game = start(seed);
        TablaUtilService service = service();
        Dice pair = realDice.roll(seed, GAME_ID, 0);

        service.openingThrow(game, black);
        service.openingThrow(game, white);

        TablaGameState state = game.getTablaState();
        Player starter = pair.d1() > pair.d2() ? white : black;
        assertFalse(service.isOpening(game));
        assertEquals(starter, state.getInTurnPlayer());
        assertEquals(starter, state.getFirstTurnPlayer());
        assertTrue(state.isRolled(), "no second throw: the opening dice are the first turn");
        assertEquals(pair.d1(), state.getDie1());
        assertEquals(pair.d2(), state.getDie2());
        assertArrayEquals(pair.values(), state.remainingDiceValues());
        assertEquals(2, state.getMaxDiceUsable());
        assertEquals(state.getBoard(), state.getTurnStartBoard());
        assertEquals(1, state.getTurnIndex(), "the next roll draws a fresh pair");

        // Both players see the throw that decided it.
        List<OpeningThrowDTO> view = service.buildState(game, "petko91").openingThrows();
        assertEquals(List.of(new OpeningThrowDTO(pair.d1(), pair.d2())), view);
    }

    @Test
    @DisplayName("equal dice are shown as a tie, and both players throw again")
    void aTieIsThrownAgain() {
        byte[] seed = seedWhere(true);
        Game game = start(seed);
        TablaUtilService service = service();
        Dice tie = realDice.roll(seed, GAME_ID, 0);

        service.openingThrow(game, white);
        service.openingThrow(game, black);

        TablaGameState state = game.getTablaState();
        assertTrue(service.isOpening(game), "a tie decides nothing");
        assertNull(state.getDie1());
        assertNull(state.getDie2());
        assertEquals(1, state.getTurnIndex(), "the next throw is a new pair");

        // The tie stays on the record, for both.
        assertEquals(List.of(new OpeningThrowDTO(tie.d1(), tie.d2())),
                service.buildState(game, "ninja2011").openingThrows());

        // Throw until it is settled; every throw comes from the seed, in order.
        int deciding = realDice.openingRollIndexUsed(seed, GAME_ID, 0);
        for (int i = 1; i <= deciding; i++) {
            service.openingThrow(game, black);
            service.openingThrow(game, white);
        }
        Dice decided = realDice.roll(seed, GAME_ID, deciding);
        assertFalse(service.isOpening(game));
        assertEquals(decided.d1(), state.getDie1());
        assertEquals(deciding + 1, state.getTurnIndex());

        List<OpeningThrowDTO> record = service.buildState(game, "petko91").openingThrows();
        assertEquals(deciding + 1, record.size());
        record.subList(0, deciding).forEach(t -> assertEquals(t.mine(), t.opponent()));
        assertNotEquals(record.getLast().mine(), record.getLast().opponent());
    }

    /* ---------------- nobody there ---------------- */

    @Test
    @DisplayName("nothing is ever thrown for a player: one who lets the time run out loses, as with any turn")
    void aPlayerWhoDoesNotThrowLoses() {
        Game game = start(seedWhere(false));
        when(gameUtilService.findGameById(GAME_ID)).thenReturn(Optional.of(game));
        TablaUtilService service = service();

        service.openingThrow(game, white);
        assertTrue(service.openingTimedOut(GAME_ID));

        assertNull(game.getTablaState().getDie2(), "black's die was not thrown for them");
        assertEquals(white, game.getWinner());
    }

    @Test
    @DisplayName("when neither has thrown, neither is singled out — the window opens again")
    void neitherThrewNobodyLoses() throws Exception {
        Game game = start(seedWhere(false));
        when(gameUtilService.findGameById(GAME_ID)).thenReturn(Optional.of(game));
        TablaUtilService service = service();
        java.time.Instant before = game.getTablaState().getNextMoveTime();

        Thread.sleep(5);
        assertTrue(service.openingTimedOut(GAME_ID));

        assertNull(game.getWinner());
        assertTrue(service.isOpening(game));
        assertNull(game.getTablaState().getDie1());
        assertNull(game.getTablaState().getDie2());
        assertTrue(game.getTablaState().getNextMoveTime().isAfter(before), "a fresh window");
    }

    @Test
    @DisplayName("a player still to throw has a clock, extensions and warnings — as on turn")
    void whoHasToAct() {
        byte[] seed = seedWhere(false);
        Game game = start(seed);
        TablaUtilService service = service();

        assertTrue(service.mustAct(game, white));
        assertTrue(service.mustAct(game, black));
        assertNotNull(service.buildState(game, "petko91").nextMoveTimeInSeconds());

        service.openingThrow(game, white);
        assertFalse(service.mustAct(game, white), "white has thrown");
        assertTrue(service.mustAct(game, black));
        assertNull(service.buildState(game, "petko91").nextMoveTimeInSeconds(), "no clock for white now");
        assertNotNull(service.buildState(game, "ninja2011").nextMoveTimeInSeconds());

        service.openingThrow(game, black);
        Player starter = game.getTablaState().getInTurnPlayer();
        assertTrue(service.mustAct(game, starter));
        assertFalse(service.mustAct(game, game.getOpponent(starter)));

        // Past the opening, the scheduler goes back to the ordinary rules.
        when(gameUtilService.findGameById(GAME_ID)).thenReturn(Optional.of(game));
        assertFalse(service.openingTimedOut(GAME_ID));
    }

    @Test
    @DisplayName("the blocked-roll pass leaves the opening alone")
    void aBlockedPassIsNotAnOpening() {
        Game game = start(seedWhere(false));
        when(gameUtilService.findGameById(GAME_ID)).thenReturn(Optional.of(game));

        assertFalse(service().passIfBlocked(GAME_ID));
        assertTrue(service().isOpening(game));
    }

    /* ---------------- after the first turn ---------------- */

    @Test
    @DisplayName("once the starter's turn is over, the opening is no longer reported")
    void goneAfterTheFirstTurn() {
        Game game = start(seedWhere(false));
        TablaUtilService service = service();
        service.openingThrow(game, white);
        service.openingThrow(game, black);
        TablaGameState state = game.getTablaState();
        assertNotNull(service.buildState(game, "petko91").openingThrows());

        // What endTurn does when the starter confirms.
        state.clearTurn();
        state.setInTurnPlayer(game.getOpponent(state.getInTurnPlayer()));

        assertNull(service.buildState(game, "petko91").openingThrows());
        assertNull(service.buildState(game, "ninja2011").openingThrows());
        assertFalse(service.buildState(game, "petko91").openingPhase());
    }

    @Test
    @DisplayName("a game begun under the old rule, where the starter throws afresh, never reports one")
    void notForAGameBegunBeforeTheRule() {
        byte[] seed = seedWhere(false);
        Game game = start(seed);
        TablaGameState state = game.getTablaState();
        TablaUtilService service = service();

        // Old rule: a starter already chosen, on turn, before throwing.
        state.setInTurnPlayer(white);
        state.setFirstTurnPlayer(white);
        state.setTurnIndex(1);
        assertNull(service.buildState(game, "petko91").openingThrows());
        assertFalse(service.buildState(game, "petko91").openingPhase());

        // And after that throw: a fresh pair, which moved the turn index on.
        state.setTurnIndex(2);
        service.placeDice(game, white, realDice.roll(seed, GAME_ID, 1));
        assertNull(service.buildState(game, "petko91").openingThrows());
    }

    /* ---------------- helpers ---------------- */

    private Game start(byte[] seed) {
        TablaDiceService fixedSeed = new TablaDiceService() {
            @Override
            public byte[] newSeed() {
                return seed.clone();
            }
        };
        return service(fixedSeed).startGame(white, black);
    }

    private TablaUtilService service() {
        return service(realDice);
    }

    private TablaUtilService service(TablaDiceService dice) {
        return new TablaUtilService(gameUtilService, mock(WebSocketService.class), mock(TablaStatsService.class), dice);
    }

    /** A seed whose first opening throw is — or is not — a tie, for this game id. */
    private byte[] seedWhere(boolean opensOnATie) {
        for (int k = 0; k < 10_000; k++) {
            byte[] seed = new byte[32];
            Arrays.fill(seed, (byte) k);
            seed[0] = (byte) (k >> 8);
            if (realDice.roll(seed, GAME_ID, 0).isDouble() == opensOnATie) return seed;
        }
        throw new IllegalStateException("no such seed in range");
    }

    private static Player seat(String username) {
        User user = new User();
        user.setUsername(username);
        Player player = new Player();
        player.setUser(user);
        // Entities compare by id, and two seats must not be equal.
        setId(player, UUID.randomUUID());
        return player;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = Class.forName("bg.deck.common.model.base.BaseEntity").getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
