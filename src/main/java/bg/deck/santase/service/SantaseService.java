package bg.deck.santase.service;

import bg.deck.common.constant.LogConstants;
import bg.deck.common.enums.GameType;
import bg.deck.santase.enums.Rank;
import bg.deck.santase.exception.CardNotFoundException;
import bg.deck.santase.exception.NoCardForReplacingException;
import bg.deck.santase.exception.NotFirstInTurnException;
import bg.deck.santase.exception.NotInTurnException;
import bg.deck.common.exception.PlayerInactivitySurrenderException;
import bg.deck.common.exception.UserNotPartOfGameException;
import bg.deck.santase.model.Card;
import bg.deck.common.model.Game;
import bg.deck.santase.model.GameState;
import bg.deck.common.model.Player;
import bg.deck.santase.model.request.CardRequest;
import bg.deck.common.model.response.SearchGameResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.service.GameUtilService;
import bg.deck.common.util.AuthenticatedUser;
import bg.deck.common.service.WebSocketService;

@Log4j2
@RequiredArgsConstructor
@Service
public class SantaseService {
    private final WebSocketService webSocketService;
    private final WebSocketUtilService webSocketUtilService;
    private final GameUtilService gameUtilService;
    private final SantaseDealService santaseDealService;
    private final SantaseTurnTimer santaseTurnTimer;
    private final AvailabilityService availabilityService;

    private final Queue<String> matchQueue = new ConcurrentLinkedQueue<>();

    public void getGameState() {
        String username = AuthenticatedUser.username();

        log.info(LogConstants.GET_STATE_LOG, username);

        Game game = santaseDealService.findGameByUsername(username);

        if (!game.getFirstPlayer().getUsername().equals(username) && !game.getSecondPlayer().getUsername().equals(username)) {
            throw new UserNotPartOfGameException(username);
        }

        webSocketUtilService.updateGameState(game, username);

        log.info(LogConstants.GOT_STATE_LOG, username);
    }

    /**
     * Points a player who opens the game back at the one they are already in.
     *
     * @return whether they have one; if so its id has just been sent on their
     *         search topic, exactly as a search with a game in progress sends it
     */
    public boolean resumeActiveGame() {
        String username = AuthenticatedUser.username();
        return !gameUtilService.checkIfUserExistsAndIsAvailable(username, GameType.SANTASE);
    }

    public void searchGame() {
        String username = AuthenticatedUser.username();
        log.info(LogConstants.GAME_SEARCH_START, username);

        // Nobody joins the queue for a game they are not being offered.
        // Only the start is gated: switching сантасе off stops new games and
        // lets the tables already being played finish.
        availabilityService.requireAvailable(GameType.SANTASE.name(), username);

        if (!gameUtilService.checkIfUserExistsAndIsAvailable(username, GameType.SANTASE)) {
            log.warn(LogConstants.GAME_SEARCH_USER_UNAVAILABLE, username);
            return;
        }

        if (matchQueue.contains(username)) {
            log.info(LogConstants.GAME_SEARCH_ALREADY_IN_QUEUE, username);
            webSocketService.notifyGameSearch(username, SearchGameResponse.waiting());
            return;
        }

        String waitingPlayerUsername = matchQueue.poll();

        if (waitingPlayerUsername == null) {
            matchQueue.offer(username);
            log.info(LogConstants.GAME_SEARCH_ADDED_TO_QUEUE, username);
            webSocketService.notifyGameSearch(username, SearchGameResponse.waiting());
        } else {
            // A fresh seat per game: player rows carry per-game mutable state.
            Player firstPlayer = gameUtilService.newPlayerFor(username);
            Player secondPlayer = gameUtilService.newPlayerFor(waitingPlayerUsername);

            Game newGame = santaseDealService.startGame(firstPlayer, secondPlayer);

            log.info(LogConstants.GAME_SEARCH_MATCH_FOUND,
                    firstPlayer.getUsername(),
                    secondPlayer.getUsername(),
                    newGame.getId());

            webSocketService.notifyGameSearch(List.of(firstPlayer.getUsername(), secondPlayer.getUsername()),
                    SearchGameResponse.started(newGame.getId()));
            santaseTurnTimer.update(newGame);
        }
    }

    public void cancelSearchGame(String username) {
        log.info(LogConstants.GAME_SEARCH_CANCEL_START, username);

        boolean removed = matchQueue.remove(username);

        if (removed) {
            log.info(LogConstants.GAME_SEARCH_CANCEL_SUCCESS, username);
        } else {
            log.info(LogConstants.GAME_SEARCH_CANCEL_NOT_IN_QUEUE, username);
        }
    }

    @Transactional
    public void playCard(CardRequest cardRequest) {
        String username = AuthenticatedUser.username();
        log.info(LogConstants.PLAY_CARD_START, username, cardRequest.cardId());

        Game game = santaseDealService.findGameByUsername(username);
        Player player = game.getPlayerByUsername(username);
        GameState state = game.getState();

        if (!state.isInTurn(player)) {
            throw new NotInTurnException(username);
        }

        Card cardForRemoval = player.getHand().stream()
                .filter(c -> c.getId().equals(cardRequest.cardId()))
                .filter(Card::getIsPlayable)
                .findFirst()
                .orElseThrow(() -> new CardNotFoundException(username));

        santaseDealService.removeCardFromHand(game, player, cardForRemoval);
        player.setPlayedCard(cardForRemoval);
        player.getHand().forEach(card -> card.setIsPlayable(true));

        if (game.getFirstPlayer().getPlayedCard() != null && game.getSecondPlayer().getPlayedCard() != null) {
            log.info(LogConstants.PLAY_CARD_TRICK_EVALUATING,
                    game.getFirstPlayer().getPlayedCard(), game.getSecondPlayer().getPlayedCard());

            webSocketUtilService.updateGameStateWithTrickTaker(game,
                    santaseDealService.determineWinner(game).getUsername());
            santaseDealService.evaluateTrick(game);
        } else {
            log.info(LogConstants.PLAY_CARD_SUCCESS, username, cardForRemoval.getId());
            state.setInTurnPlayer(game.getOpponent(player));
        }

        gameUtilService.saveGame(game);

        webSocketUtilService.updateGameState(game);
        santaseTurnTimer.update(game);
    }

    @Transactional
    public void announceCombination(CardRequest cardRequest) {
        String username = AuthenticatedUser.username();
        log.info(LogConstants.ANNOUNCE_START, username, cardRequest.cardId());

        Game game = santaseDealService.findGameByUsername(username);

        Player player = game.getPlayerByUsername(username);

        GameState state = game.getState();

        if (!state.isInTurn(player)) {
            throw new NotInTurnException(username);
        }

        if (!state.getFirstTurnPlayer().equals(player)) {
            throw new NotFirstInTurnException(username);
        }

        Card card = player.getHand().stream()
                .filter(c -> c.getId().equals(cardRequest.cardId()))
                .findFirst()
                .orElseThrow(() -> new CardNotFoundException(username));

        if (santaseDealService.checkTwentyForty(game, player, card)) {
            log.info(LogConstants.ANNOUNCE_SUCCESS, username, player.getBonus());

            game.getState().extendNextMoveTime();
            webSocketUtilService.updateGameState(game);

            player.setBonus(null);
            gameUtilService.saveGame(game);
            santaseTurnTimer.update(game);
        }
    }

    @Transactional
    public void closeDeck() {
        String username = AuthenticatedUser.username();
        log.info(LogConstants.CLOSE_DECK_START, username);

        Game game = santaseDealService.findGame(username);
        Player player = game.getPlayerByUsername(username);

        GameState state = game.getState();
        state.getDeck().clear();
        state.setClosedByPlayer(player);
        state.extendNextMoveTime();
        santaseDealService.saveGameState(state);

        log.info(LogConstants.CLOSE_DECK_SUCCESS, username);

        webSocketUtilService.updateGameState(game);
        santaseTurnTimer.update(game);
    }

    @Transactional
    public void replaceCard() {
        String username = AuthenticatedUser.username();

        Game game = santaseDealService.findGame(username);
        GameState state = game.getState();
        Player player = game.getPlayerByUsername(username);

        log.info(LogConstants.REPLACE_CARD_START, username, state.getTrumpCard().getSuit());

        List<Card> playerCards = player.getHand();

        Card nineOfTrumps = playerCards.stream()
                .filter(card -> card.getRank() == Rank.NINE)
                .filter(card -> card.getSuit() == state.getTrumpCard().getSuit())
                .findFirst()
                .orElseThrow(() -> new NoCardForReplacingException(username));

        Card currentTrump = state.getTrumpCard();

        playerCards.remove(nineOfTrumps);
        playerCards.add(currentTrump);

        state.setTrumpCard(nineOfTrumps);

        state.getDeck().removeLast();
        state.getDeck().addLast(nineOfTrumps);
        state.extendNextMoveTime();
        santaseDealService.saveGameState(state);
        log.info(LogConstants.REPLACE_CARD_SUCCESS, username, currentTrump.getRank());

        webSocketUtilService.updateGameState(game);
        santaseTurnTimer.update(game);
    }

    @Transactional
    public void finishDeal() {
        String username = AuthenticatedUser.username();
        log.info(LogConstants.FINISH_DEAL_START, username);

        Game game = santaseDealService.findGameByUsername(username);
        GameState state = game.getState();
        Player player = game.getPlayerByUsername(username);

        if (!state.getFirstTurnPlayer().equals(player)) {
            throw new NotFirstInTurnException(username);
        }

        if (!state.isInTurn(player)) {
            throw new NotInTurnException(username);
        }

        Player opponentPlayer = game.getOpponent(player);
        int pointsAwarded;
        Player trickWinner;

        if (player.getScore() >= 66) {
            boolean isBlanked = opponentPlayer.getIsBlanked();
            if (isBlanked) {
                pointsAwarded = 3;
            } else if (opponentPlayer.getScore() < 33) {
                pointsAwarded = 2;
            } else {
                pointsAwarded = 1;
            }
            player.setResult(player.getResult() + pointsAwarded);
            trickWinner = player;
        } else {
            pointsAwarded = 3;
            opponentPlayer.setResult(opponentPlayer.getResult() + pointsAwarded);
            trickWinner = opponentPlayer;
        }

        log.info(LogConstants.FINISH_DEAL_SUCCESS, trickWinner.getUsername(), pointsAwarded);

        webSocketUtilService.updateGameStateWithTrickWinner(game, trickWinner.getUsername());

        santaseDealService.prepareNewState(game, trickWinner);
        gameUtilService.saveGame(game);

        webSocketUtilService.updateGameState(game);
        santaseTurnTimer.update(game);
    }

    @Transactional
    public void surrender() {
        String username = AuthenticatedUser.username();
        Game game = santaseDealService.findGameByUsername(username);

        if (game.getWinner() != null) {
            return;
        }

        Player opponentPlayer = game.getOpponentPlayerByUsername(username);

        log.info(LogConstants.FINISH_GAME_SURRENDER, username, opponentPlayer.getUsername());

        game.getFirstPlayer().setPlayedCard(null);
        game.getSecondPlayer().setPlayedCard(null);
        game.getFirstPlayer().setHand(new ArrayList<>());
        game.getSecondPlayer().setHand(new ArrayList<>());

        gameUtilService.setGameWinner(game, opponentPlayer, true);
        log.info(
                LogConstants.FINISH_GAME,
                game.getId(),
                opponentPlayer.getUsername(),
                game.getFirstPlayer().getUsername(),
                game.getFirstPlayer().getResult(),
                game.getSecondPlayer().getUsername(),
                game.getSecondPlayer().getResult()
        );

        gameUtilService.saveGame(game);
        webSocketUtilService.updateGameState(game);
        santaseTurnTimer.update(game);
    }

    @Transactional
    public void inactivity() {
        String username = AuthenticatedUser.username();
        Game game = santaseDealService.findGameByUsername(username);
        Player player = game.getPlayerByUsername(username);

        // A report sent before the clock ran out still counts. It looks like a
        // way to buy thinking time, and it is — but only three times: the count
        // it raises is what ends the game at three. Dropping the early ones
        // instead removes that ceiling, because the client shows its Continue
        // popup on any answer that is not a refusal and /extend-time only
        // refuses once the count reaches three. Capping it properly means the
        // server telling the client whether the timeout was accepted, and
        // extend-time honouring that — a change on both sides, not here.
        int newInactivityCount = player.getInactivityCount() + 1;
        player.setInactivityCount(newInactivityCount);

        log.info(LogConstants.PLAYER_INACTIVITY_TIMEOUT, username, game.getId(), newInactivityCount);

        gameUtilService.saveGame(game);

        if (newInactivityCount == 3) {
            log.warn(LogConstants.PLAYER_FORCED_SURRENDER_BY_INACTIVITY, username, game.getId());
            throw new PlayerInactivitySurrenderException();
        }
        santaseTurnTimer.update(game);
    }

    @Transactional
    public void extendNextMoveTime() {
        String username = AuthenticatedUser.username();
        Game game = santaseDealService.findGameByUsername(username);
        Player player = game.getPlayerByUsername(username);

        if (player.getInactivityCount() == 3) {
            log.warn(LogConstants.PLAYER_FORCED_SURRENDER_BY_INACTIVITY, username, game.getId());
            throw new PlayerInactivitySurrenderException();
        }

        GameState gameState = game.getState();
        gameState.extendNextMoveTime();

        log.info(LogConstants.EXTEND_NEXT_MOVE_TIME, username, game.getId());

        gameUtilService.saveGame(game);
        santaseTurnTimer.update(game);
        // The opponent is watching this clock burn down: a fresh budget
        // has to reach their screen, or the bar sits empty while the player
        // it belongs to still has twenty seconds.
        webSocketUtilService.updateGameState(game, game.getOpponent(player).getUsername());
    }
}
