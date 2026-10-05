package bg.deck.tabla.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.Game;
import bg.deck.common.model.Player;
import bg.deck.tabla.model.TablaGameState;
import bg.deck.tabla.model.dto.ComboHopDTO;
import bg.deck.tabla.model.dto.HopDTO;
import bg.deck.tabla.model.dto.OpeningThrowDTO;
import bg.deck.tabla.model.response.TablaStateResponse;
import bg.deck.tabla.engine.BackgammonRules;
import bg.deck.tabla.engine.BoardState;
import bg.deck.tabla.engine.Dice;
import bg.deck.tabla.enums.GameResultKind;
import bg.deck.tabla.engine.Hop;
import bg.deck.tabla.enums.Side;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import bg.deck.common.service.GameUtilService;
import bg.deck.common.service.RankingService;
import bg.deck.common.service.WebSocketService;

/**
 * Table lifecycle for табла: creating games, building the per-player payload and
 * ending games. {@link TablaService} holds the request-facing operations.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class TablaUtilService {

    private final GameUtilService gameUtilService;
    private final WebSocketService webSocketService;
    private final RankingService rankingService;
    private final TablaDiceService diceService;

    /* ------------------------------------------------------------------
       Lifecycle
       ------------------------------------------------------------------ */

    /**
     * Starts a game with the opening roll used only to decide who begins.
     *
     * The opening roll used to double as the starter's first dice, so the game
     * opened with dice already on the table and no way to throw them. Every turn
     * now starts the same way — press Хвърли — including the first.
     */
    @Transactional
    public Game startGame(Player firstPlayer, Player secondPlayer) {
        firstPlayer.setInactivityCount(0);
        secondPlayer.setInactivityCount(0);

        byte[] seed = diceService.newSeed();

        TablaGameState state = TablaGameState.builder()
                .board(BoardState.initial().encode())
                .turnIndex(0)
                .maxDiceUsable(0)
                .build();

        Game game = Game.builder()
                .gameType(GameType.TABLA)
                .firstPlayer(firstPlayer)
                .secondPlayer(secondPlayer)
                .tablaState(state)
                .serverSeed(seed)
                .serverSeedHash(diceService.hash(seed))
                .build();

        game = gameUtilService.saveGame(game);

        // Nobody is on turn yet: each player throws one die first, with the same
        // time — and the same warning — as any turn.
        state.extendNextMoveTime();
        return gameUtilService.saveGame(game);
    }

    /* ------------------------------------------------------------------
       The opening roll
       ------------------------------------------------------------------ */

    /**
     * True until someone has won the opening roll: nobody is on turn yet.
     *
     * <p>The phase needs no column of its own. The turn index is the throw in
     * progress, and die1/die2 hold whichever of its two dice have been thrown
     * — the first player's (WHITE) and the second player's.
     */
    public boolean isOpening(Game game) {
        return game.getWinner() == null && game.getTablaState().getInTurnPlayer() == null;
    }

    /**
     * One player throws their die of the opening roll. Returns false when they
     * already had — a double tap changes nothing.
     *
     * <p>The value is not chosen here: every throw is derived from the seed that
     * was committed when the game began, so tapping reveals the die, and nobody
     * can throw again for a better one. Once both dice are out the throw is
     * settled — see {@link #settleOpening}.
     */
    public boolean openingThrow(Game game, Player player) {
        TablaGameState state = game.getTablaState();
        boolean first = player.equals(game.getFirstPlayer());
        if ((first ? state.getDie1() : state.getDie2()) != null) {
            return false;
        }

        Dice pair = diceService.roll(game.getServerSeed(), game.getId(), state.getTurnIndex());
        if (first) {
            state.setDie1(pair.d1());
        } else {
            state.setDie2(pair.d2());
        }

        if (state.getDie1() != null && state.getDie2() != null) {
            settleOpening(game, pair);
        }
        return true;
    }

    /**
     * Both dice are out. Equal dice are thrown again by both players; otherwise
     * the higher one starts, and starts by playing those two dice — no second
     * throw. The turn index moves past every throw the opening used, so the next
     * roll of the game still draws unseen dice from the seed.
     */
    private void settleOpening(Game game, Dice pair) {
        TablaGameState state = game.getTablaState();
        state.setTurnIndex(state.getTurnIndex() + 1);

        if (pair.isDouble()) {
            state.setDie1(null);
            state.setDie2(null);
            state.extendNextMoveTime();
            return;
        }

        Player starter = pair.d1() > pair.d2() ? game.getFirstPlayer() : game.getSecondPlayer();
        state.setFirstTurnPlayer(starter);
        state.setInTurnPlayer(starter);
        placeDice(game, starter, pair);
    }

    /**
     * Whether this player has something to do right now, and so a clock
     * running against them: on turn, or in the opening with their die still
     * unthrown. Time extensions and the inactivity count use this, so the
     * opening throw has exactly the time and warnings a turn has.
     */
    public boolean mustAct(Game game, Player player) {
        TablaGameState state = game.getTablaState();
        if (isOpening(game)) {
            return (player.equals(game.getFirstPlayer()) ? state.getDie1() : state.getDie2()) == null;
        }
        return state.isInTurn(player);
    }

    /**
     * Called by the scheduler when the opening window runs out. Returns true
     * when the game was in its opening.
     *
     * <p>Nothing is ever thrown for a player. One who let the time run out
     * while the other had thrown is treated as any player who let a turn run
     * out: the game is lost. When neither has thrown, neither is singled out —
     * the window is simply opened again, and either of them can come back and
     * throw, or leave.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean openingTimedOut(UUID gameId) {
        Game game = gameUtilService.findGameById(gameId).orElse(null);
        if (game == null || !isOpening(game)) {
            return false;
        }

        TablaGameState state = game.getTablaState();
        boolean firstThrew = state.getDie1() != null;
        boolean secondThrew = state.getDie2() != null;

        if (firstThrew != secondThrew) {
            Player absent = firstThrew ? game.getSecondPlayer() : game.getFirstPlayer();
            log.info("Табла {}: {} did not throw the opening die in time — the game is lost", gameId, absent.getUsername());
            finishGame(game, game.getOpponent(absent), true);
            return true;
        }

        log.info("Табла {}: neither player threw the opening die — the window opens again", gameId);
        state.extendNextMoveTime();
        gameUtilService.saveGame(game);
        pushToBoth(game);
        return true;
    }

    /**
     * Puts a throw on the table for the player on turn: the dice, how many of
     * them the position lets them use, and the board to undo back to. A normal
     * roll and the opening roll both start a turn this way.
     */
    public void placeDice(Game game, Player player, Dice dice) {
        TablaGameState state = game.getTablaState();
        state.setDie1(dice.d1());
        state.setDie2(dice.d2());
        state.setRemainingDiceValues(dice.values());
        state.setPendingHopList(List.of());
        state.snapshotTurnStart();
        state.setMaxDiceUsable(BackgammonRules.maxUsed(state.boardState(), sideOf(game, player), dice.values()));
        // The clock is extended on roll and confirm only — see move()/undo().
        state.extendNextMoveTime();
    }

    /**
     * The finished throws of the opening roll, from this player's side: the
     * ties so far while it is being thrown, then all of them — ties and the
     * decider — while the starter plays the opening dice. Null from then on.
     *
     * <p>A game that began before the opening dice were played rolls a fresh
     * pair on its first turn; that roll moves the turn index past the opening,
     * so such a game never reports one.
     */
    private List<OpeningThrowDTO> openingThrows(Game game, Player player) {
        TablaGameState state = game.getTablaState();
        if (game.getWinner() != null) {
            return null;
        }

        int completed;
        if (isOpening(game)) {
            // Every throw before the one in progress was a tie.
            completed = state.getTurnIndex();
        } else {
            Player starter = state.getFirstTurnPlayer();
            if (starter == null || !state.isInTurn(starter) || !state.isRolled()) {
                return null;
            }
            int deciding = diceService.openingRollIndexUsed(game.getServerSeed(), game.getId(), 0);
            if (state.getTurnIndex() != deciding + 1) {
                return null;
            }
            completed = deciding + 1;
        }

        boolean first = player.equals(game.getFirstPlayer());
        return IntStream.range(0, completed)
                .mapToObj(i -> diceService.roll(game.getServerSeed(), game.getId(), i))
                .map(d -> first ? new OpeningThrowDTO(d.d1(), d.d2()) : new OpeningThrowDTO(d.d2(), d.d1()))
                .toList();
    }

    public Game findActiveGame(String username) {
        return gameUtilService.findGameByUsername(username, GameType.TABLA);
    }

    public Side sideOf(Game game, Player player) {
        return game.getFirstPlayer().equals(player) ? Side.WHITE : Side.BLACK;
    }

    /* ------------------------------------------------------------------
       Ending
       ------------------------------------------------------------------ */

    @Transactional
    public void finishGame(Game game, Player winner, boolean opponentSurrendered) {
        if (game.getWinner() != null) {
            return;
        }
        game.setWinner(winner, opponentSurrendered);
        rankingService.updateRankingAfterGame(game);
        gameUtilService.saveGame(game);
        pushToBoth(game);
    }

    /**
     * Hands the turn on when the clock runs out on a roll that has no legal
     * move. Returns true when it did.
     *
     * <p>Such a turn is the one case where sitting still is not a choice: the
     * dice decided it, and the player has nothing to play. The client passes
     * for them a couple of seconds after the roll, but it can only do that
     * while it is open — close the app on a blocked roll and the clock ran
     * out, which for табла means losing the game outright. The pass now
     * happens on the server too, so the outcome no longer depends on whether
     * anyone was watching.
     *
     * <p>Nothing else about the turn changes: this is the same hand-off the
     * player's own pass performs, and {@code setInTurnPlayer} starts the
     * opponent's clock.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean passIfBlocked(UUID gameId) {
        Game game = gameUtilService.findGameById(gameId).orElse(null);
        if (game == null || game.getWinner() != null) {
            return false;
        }

        TablaGameState state = game.getTablaState();
        if (state == null || isOpening(game) || !state.isRolled() || state.getMaxDiceUsable() != 0) {
            return false;
        }

        Player blocked = state.getInTurnPlayer();
        log.info("Табла: {} timed out with no legal move — the turn passes instead", blocked.getUsername());

        state.clearTurn();
        state.setInTurnPlayer(game.getOpponent(blocked));
        gameUtilService.saveGame(game);
        pushToBoth(game);
        return true;
    }

    /**
     * Called by the scheduler when a player's clock runs out. Mirrors the Santase
     * path, including the guard against a game that already finished normally.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surrenderByInactivity(UUID gameId) {
        Game game = gameUtilService.findGameById(gameId).orElse(null);
        if (game == null || game.getWinner() != null) {
            return;
        }

        Player timedOut = game.getTablaState().getInTurnPlayer();
        Player opponent = game.getOpponent(timedOut);

        log.info("Табла: {} timed out, {} wins", timedOut.getUsername(), opponent.getUsername());
        finishGame(game, opponent, true);
    }

    /* ------------------------------------------------------------------
       Pushing state
       ------------------------------------------------------------------ */

    public void pushToBoth(Game game) {
        push(game, game.getFirstPlayer().getUsername());
        push(game, game.getSecondPlayer().getUsername());
    }

    public void push(Game game, String username) {
        webSocketService.notifyGameUpdate(game.getId().toString(), username, buildState(game, username));
    }

    /** The position from one player's point of view, with their legal moves. */
    public TablaStateResponse buildState(Game game, String username) {
        Player player = game.getPlayerByUsername(username);
        TablaGameState state = game.getTablaState();

        Side side = sideOf(game, player);
        Side other = side.opponent();
        BoardState board = state.boardState();

        boolean onTurn = state.isInTurn(player);
        // During the opening die1/die2 are the opening throw in progress, not a
        // roll: they are sent as openingMine/openingOpponent instead, so the
        // table's own dice row stays empty until somebody starts.
        boolean opening = isOpening(game);
        boolean first = player.equals(game.getFirstPlayer());
        int[] remaining = opening ? new int[0] : state.remainingDiceValues();
        int used = state.usedDiceCount();

        List<HopDTO> legal = onTurn && game.getWinner() == null
                ? BackgammonRules.legalTurnHops(board, side, remaining, used, state.getMaxDiceUsable())
                        .stream().map(HopDTO::from).toList()
                : List.of();

        List<ComboHopDTO> combos = onTurn && game.getWinner() == null
                ? BackgammonRules.legalComboHops(board, side, remaining, used, state.getMaxDiceUsable())
                        .stream().map(ComboHopDTO::from).toList()
                : List.of();

        List<HopDTO> pending = state.pendingHopList().stream().map(HopDTO::from).toList();

        GameResultKind kind = game.getWinner() == null
                ? null
                : BackgammonRules.resultKind(board, sideOf(game, game.getWinner()));

        return TablaStateResponse.builder()
                .gameId(game.getId().toString())
                .gameType(GameType.TABLA.name())
                .firstPlayerUsername(game.getFirstPlayer().getUsername())
                .secondPlayerUsername(game.getSecondPlayer().getUsername())
                .mySide(side.name())
                .points(IntStream.rangeClosed(1, BoardState.POINTS).boxed().map(board::at).toList())
                .myBar(board.bar(side))
                .opponentBar(board.bar(other))
                .myOff(board.off(side))
                .opponentOff(board.off(other))
                .myPipCount(board.pipCount(side))
                .opponentPipCount(board.pipCount(other))
                .isOnTurn(onTurn)
                .die1(opening ? null : state.getDie1())
                .die2(opening ? null : state.getDie2())
                .remainingDice(Arrays.stream(remaining).boxed().toList())
                .maxDiceUsable(state.getMaxDiceUsable())
                .usedDiceCount(used)
                .mustConfirm(onTurn && state.isRolled() && used == state.getMaxDiceUsable()
                        && state.getMaxDiceUsable() > 0)
                .noMovesAvailable(onTurn && state.isRolled() && state.getMaxDiceUsable() == 0)
                .legalHops(legal)
                .comboHops(combos)
                .pendingHops(pending)
                .winnerUsername(game.getWinner() != null ? game.getWinner().getUsername() : null)
                .surrenderPlayerUsername(game.getSurrenderPlayer() != null
                        ? game.getSurrenderPlayer().getUsername() : null)
                .resultKind(kind != null ? kind.name() : null)
                .inactivityCount(player.getInactivityCount() != null ? player.getInactivityCount() : 0)
                .openingPhase(opening)
                .openingThrows(openingThrows(game, player))
                .openingMine(opening ? (first ? state.getDie1() : state.getDie2()) : null)
                .openingOpponent(opening ? (first ? state.getDie2() : state.getDie1()) : null)
                .nextMoveTimeInSeconds(mustAct(game, player) && game.getWinner() == null && state.getNextMoveTime() != null
                        ? Math.toIntExact(Math.max(0,
                                Duration.between(Instant.now(), state.getNextMoveTime()).getSeconds()))
                        : null)
                .serverSeedHash(game.getServerSeedHash())
                // The seed is what makes past rolls verifiable, so it must stay
                // secret until there are no future rolls left to predict.
                .serverSeed(game.getWinner() != null && game.getServerSeed() != null
                        ? HexFormat.of().formatHex(game.getServerSeed()) : null)
                .build();
    }

    /** Convenience for tests and the service layer. */
    public List<Hop> legalHops(Game game, Player player) {
        TablaGameState state = game.getTablaState();
        return BackgammonRules.legalTurnHops(state.boardState(), sideOf(game, player),
                state.remainingDiceValues(), state.usedDiceCount(), state.getMaxDiceUsable());
    }
}
