package bg.deck.tabla.service;

import bg.deck.common.constant.LogConstants;
import bg.deck.common.enums.GameType;
import bg.deck.common.exception.PlayerInactivitySurrenderException;
import bg.deck.tabla.exception.TablaException;
import bg.deck.common.model.Game;
import bg.deck.common.model.Player;
import bg.deck.tabla.model.TablaGameState;
import bg.deck.tabla.model.request.MoveRequest;
import bg.deck.common.model.response.SearchGameResponse;
import bg.deck.tabla.engine.BackgammonRules;
import bg.deck.tabla.engine.BoardState;
import bg.deck.tabla.engine.Dice;
import bg.deck.tabla.engine.Hop;
import bg.deck.tabla.enums.Side;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.service.GameInactivityService;
import bg.deck.common.service.GameUtilService;
import bg.deck.common.service.WebSocketService;

/**
 * Обикновена табла — the request-facing operations.
 *
 * <p>The turn is applied <em>incrementally</em>: roll, then one hop per request
 * with undo available, then confirm. That matches every existing endpoint's
 * shape, lets the opponent watch checkers move rather than teleport, and keeps
 * the database authoritative so a refresh mid-turn restores exactly.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class TablaService {

    private static final int MAX_INACTIVITY = 3;

    private final TablaUtilService tablaUtilService;
    private final TablaDiceService diceService;
    private final GameUtilService gameUtilService;
    private final GameInactivityService gameInactivityService;
    private final WebSocketService webSocketService;
    private final AvailabilityService availabilityService;

    private final Queue<String> matchQueue = new ConcurrentLinkedQueue<>();

    /* ------------------------------------------------------------------
       Matchmaking
       ------------------------------------------------------------------ */

    public void searchGame() {
        String username = gameUtilService.getUsername();

        // Nobody joins the queue for a game they are not being offered.
        // Only the start is gated: switching табла off stops new games and
        // lets the tables already being played finish.
        availabilityService.requireAvailable(GameType.TABLA.name(), username);

        // Typed, so an in-progress Santase game does not block a табла search.
        if (!gameUtilService.checkIfUserExistsAndIsAvailable(username, GameType.TABLA)) {
            return;
        }

        if (matchQueue.contains(username)) {
            webSocketService.notifyGameSearch(username, GameType.TABLA, SearchGameResponse.waiting());
            return;
        }

        String waiting = matchQueue.poll();

        if (waiting == null) {
            matchQueue.offer(username);
            webSocketService.notifyGameSearch(username, GameType.TABLA, SearchGameResponse.waiting());
            return;
        }

        Player first = gameUtilService.newPlayerFor(waiting);
        Player second = gameUtilService.newPlayerFor(username);

        Game game = tablaUtilService.startGame(first, second);
        log.info("Табла match: {} vs {} ({})", waiting, username, game.getId());

        webSocketService.notifyGameSearch(List.of(waiting, username), GameType.TABLA,
                SearchGameResponse.started(game.getId()));
        gameInactivityService.updateNextMoveTime(game);
    }

    public void cancelSearch(String username) {
        matchQueue.remove(username);
    }

    public void getState() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        tablaUtilService.push(game, username);
    }

    /* ------------------------------------------------------------------
       The turn
       ------------------------------------------------------------------ */

    /**
     * This player's die of the opening roll. Either player may throw, in any
     * order; the second die settles it (see TablaUtilService#settleOpening).
     */
    @Transactional
    public void openingThrow() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        if (!tablaUtilService.isOpening(game)) {
            throw TablaException.openingOver();
        }

        if (tablaUtilService.openingThrow(game, game.getPlayerByUsername(username))) {
            gameInactivityService.updateNextMoveTime(game);
        }
        tablaUtilService.pushToBoth(game);
    }

    @Transactional
    public void roll() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        TablaGameState state = game.getTablaState();
        Player player = requireInTurn(game, username);

        if (state.isRolled()) {
            throw TablaException.diceAlreadyRolled();
        }

        Dice dice = diceService.roll(game.getServerSeed(), game.getId(), state.getTurnIndex());
        state.setTurnIndex(state.getTurnIndex() + 1);
        tablaUtilService.placeDice(game, player, dice);
        gameInactivityService.updateNextMoveTime(game);

        // A completely blocked roll used to pass the turn in the same request,
        // which wiped the dice before either player could see what was thrown.
        // The turn now stays open with noMovesAvailable set; the player passes it
        // with confirm() — usedDiceCount and maxDiceUsable are both 0, so the
        // completeness check accepts it.
        tablaUtilService.pushToBoth(game);
    }

    @Transactional
    public void move(MoveRequest request) {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        TablaGameState state = game.getTablaState();
        Player player = requireInTurn(game, username);

        if (!state.isRolled()) {
            throw TablaException.diceNotRolled();
        }

        Side side = tablaUtilService.sideOf(game, player);
        BoardState board = state.boardState();
        int[] remaining = state.remainingDiceValues();
        int used = state.usedDiceCount();

        // The destination is derived here rather than taken from the request, so
        // a client cannot desync the board by sending a mismatched 'to'.
        Hop hop = BackgammonRules
                .legalTurnHops(board, side, remaining, used, state.getMaxDiceUsable())
                .stream()
                .filter(h -> h.from() == request.from() && h.die() == request.die())
                .findFirst()
                .orElseThrow(TablaException::illegalHop);

        BoardState after = BackgammonRules.apply(board, side, hop);
        state.setBoardState(after);
        state.setRemainingDiceValues(Dice.without(remaining, hop.die()));

        List<Hop> pending = state.pendingHopList();
        pending.add(hop);
        state.setPendingHopList(pending);

        // Deliberately no clock extension: a player could otherwise stall forever
        // by moving and undoing. One turn budget covers the whole turn.
        //
        // The turn is NOT ended here even when the last die has been played.
        // Auto-confirming meant the final hop could never be undone; the player
        // now sees the finished position and commits it with confirm(). Stalling
        // is not a risk because the turn clock keeps running throughout.
        tablaUtilService.pushToBoth(game);
    }

    @Transactional
    public void undo() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        TablaGameState state = game.getTablaState();
        Player player = requireInTurn(game, username);

        List<Hop> pending = state.pendingHopList();
        if (pending.isEmpty()) {
            throw TablaException.nothingToUndo();
        }

        // Replay from the turn-start snapshot rather than inverse-applying the
        // last hop: undoing a hit would otherwise have to remember whether the
        // destination held exactly one enemy checker.
        pending.removeLast();
        Side side = tablaUtilService.sideOf(game, player);
        BoardState board = state.turnStartBoardState();
        int[] remaining = new Dice(state.getDie1(), state.getDie2()).values();

        for (Hop hop : pending) {
            board = BackgammonRules.apply(board, side, hop);
            remaining = Dice.without(remaining, hop.die());
        }

        state.setBoardState(board);
        state.setRemainingDiceValues(remaining);
        state.setPendingHopList(pending);

        tablaUtilService.pushToBoth(game);
    }

    @Transactional
    public void confirm() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        TablaGameState state = game.getTablaState();
        Player player = requireInTurn(game, username);

        confirmInternal(game, state, tablaUtilService.sideOf(game, player));
    }

    private void confirmInternal(Game game, TablaGameState state, Side side) {
        if (state.usedDiceCount() != state.getMaxDiceUsable()) {
            throw TablaException.turnNotComplete(state.usedDiceCount(), state.getMaxDiceUsable());
        }

        if (BackgammonRules.isFinished(state.boardState(), side)) {
            Player winner = state.getInTurnPlayer();
            state.clearTurn();
            tablaUtilService.finishGame(game, winner, false);
            gameInactivityService.cancel(game.getId());
            return;
        }

        endTurn(game);
    }

    private void endTurn(Game game) {
        TablaGameState state = game.getTablaState();
        Player next = game.getOpponent(state.getInTurnPlayer());

        state.clearTurn();
        state.setInTurnPlayer(next);

        gameInactivityService.updateNextMoveTime(game);
        tablaUtilService.pushToBoth(game);
    }

    /* ------------------------------------------------------------------
       Leaving and timing out
       ------------------------------------------------------------------ */

    @Transactional
    public void surrender() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);

        if (game.getWinner() != null) {
            return;
        }

        Player opponent = game.getOpponentPlayerByUsername(username);
        tablaUtilService.finishGame(game, opponent, true);
        gameInactivityService.cancel(game.getId());
    }

    @Transactional
    public void reportInactivity() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        // Deliberately unguarded: the client reads a 400 here as "allowance
        // spent" and forfeits, so a report racing the end of a turn must not
        // be refused.
        Player player = game.getPlayerByUsername(username);

        int count = (player.getInactivityCount() == null ? 0 : player.getInactivityCount()) + 1;
        player.setInactivityCount(count);

        log.info(LogConstants.PLAYER_INACTIVITY_TIMEOUT, username, game.getId(), count);

        if (count >= MAX_INACTIVITY) {
            log.warn(LogConstants.PLAYER_FORCED_SURRENDER_BY_INACTIVITY, username, game.getId());
            throw new PlayerInactivitySurrenderException();
        }

        // Re-arm against the persisted deadline. The deadline itself is NOT
        // pushed out here: the player is now inside the warning window and only
        // pressing Continue (extendTime) buys them a fresh turn.
        gameInactivityService.updateNextMoveTime(game);
        tablaUtilService.pushToBoth(game);
    }

    @Transactional
    public void extendTime() {
        String username = gameUtilService.getUsername();
        Game game = tablaUtilService.findActiveGame(username);
        requireToAct(game, username);

        game.getTablaState().extendNextMoveTime();
        gameInactivityService.updateNextMoveTime(game);
        tablaUtilService.pushToBoth(game);
    }

    /** On turn, or in the opening with their die still to throw. */
    private Player requireToAct(Game game, String username) {
        Player player = game.getPlayerByUsername(username);
        if (!tablaUtilService.mustAct(game, player)) {
            throw TablaException.notYourTurn();
        }
        return player;
    }

    private Player requireInTurn(Game game, String username) {
        Player player = game.getPlayerByUsername(username);
        if (!game.getTablaState().isInTurn(player)) {
            throw TablaException.notYourTurn();
        }
        return player;
    }
}
