package bg.deck.santase.service;

import bg.deck.common.constant.LogConstants;
import bg.deck.common.exception.NoActiveGameFoundException;
import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseSeat;
import bg.deck.santase.enums.Rank;
import bg.deck.santase.enums.Suit;
import bg.deck.santase.exception.CardNotFoundException;
import bg.deck.santase.exception.CardNotPlayableException;
import bg.deck.santase.exception.DeckSizeException;
import bg.deck.santase.exception.NotFirstInTurnException;
import bg.deck.santase.exception.NotInTurnException;
import bg.deck.santase.model.Card;
import bg.deck.santase.model.SantaseGameState;
import bg.deck.santase.repository.SantaseGameStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The rules of сантасе: dealing, what may be played on what, taking a trick,
 * the 20 and 40, scoring a deal and a game.
 *
 * <p>Santase's own, in santase's own package. Finding a game, saving it and
 * marking the winner are {@link SantaseTableService}'s, and asked for.
 *
 * <p>The only class that speaks to {@link SantaseGameStateRepository}.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class SantaseDealService {

    private final SantaseTableService santaseTableService;
    private final SantaseGameStateRepository gameStateRepository;
    private final WebSocketUtilService webSocketUtilService;
    private final SantaseStatsService santaseStatsService;

    @Transactional
    public SantaseGame startGame(SantaseSeat firstPlayer, SantaseSeat secondPlayer) {
        SantaseGameState gameState = SantaseGameState.builder().build();
        firstPlayer.setResult(0);
        secondPlayer.setResult(0);
        firstPlayer.setInactivityCount(0);
        secondPlayer.setInactivityCount(0);

        SantaseGame game = SantaseGame.builder()
                .firstPlayer(firstPlayer)
                .secondPlayer(secondPlayer)
                .state(gameState)
                .build();

        prepareNewState(game, null);
        return santaseTableService.saveGame(game);
    }

    /** This player's сантасе game in progress. */
    public SantaseGame findGameByUsername(String username) {
        return santaseTableService.findGameByUsername(username);
    }

    /**
     * This player's game, checked for closing the deck or exchanging the nine:
     * only by whoever leads, on their turn, while the deck has more than two
     * cards and is not the untouched twelve.
     */
    public SantaseGame findGame(String username) {
        SantaseGame game = findGameByUsername(username);

        SantaseSeat player = game.getPlayerByUsername(username);

        SantaseGameState state = game.getState();

        if (!state.getFirstTurnPlayer().equals(player)) {
            throw new NotFirstInTurnException(username);
        }

        if (!state.isInTurn(player)) {
            throw new NotInTurnException(username);
        }

        if (state.getDeck().size() <= 2 || state.getDeck().size() == 12) {
            throw new DeckSizeException(2, 12);
        }

        return game;
    }

    public void saveGameState(SantaseGameState gameState) {
        gameStateRepository.save(gameState);
    }

    public void removeCardFromHand(SantaseGame game, SantaseSeat player, Card cardForRemoval) {
        List<Card> playerCards = player.getHand();

        if (!playerCards.contains(cardForRemoval)) {
            throw new CardNotFoundException(player.getUsername());
        }

        // If deck still has cards → always allowed
        if (!game.getState().getDeck().isEmpty()) {
            playerCards.remove(cardForRemoval);
            return;
        }

        // First player always allowed
        if (player.equals(game.getState().getFirstTurnPlayer())) {
            playerCards.remove(cardForRemoval);
            return;
        }

        Card opponentPlayerCard = game.getOpponent(player).getPlayedCard();

        Suit opponentSuit = opponentPlayerCard.getSuit();

        boolean hasSameSuit = playerCards.stream()
                .anyMatch(card -> card.getSuit() == opponentSuit);

        // --- CASE 1: Player has same suit as opponent ---
        if (hasSameSuit) {
            List<Card> playableCards = playerCards.stream()
                    .filter(c -> c.getSuit() == opponentSuit && c.getPoints() > opponentPlayerCard.getPoints())
                    .toList();

            if (playableCards.isEmpty()) {
                playableCards = playerCards.stream()
                        .filter(c -> c.getSuit() == opponentSuit)
                        .toList();
            }

            if (!playableCards.contains(cardForRemoval)) {
                throw new CardNotPlayableException();
            }

            playerCards.remove(cardForRemoval);
            return;
        }

        // --- CASE 2: Player does NOT have same suit ---
        Card trumpCard = game.getState().getTrumpCard();
        Suit trumpSuit = trumpCard.getSuit();

        // Opponent card is trump - you may play anything
        if (opponentSuit == trumpSuit) {
            playerCards.remove(cardForRemoval);
            return;
        }

        // Otherwise check if player has trump cards
        boolean hasTrump = playerCards.stream()
                .anyMatch(c -> c.getSuit() == trumpSuit);

        if (hasTrump && cardForRemoval.getSuit() != trumpSuit) {
            throw new CardNotPlayableException();
        }

        playerCards.remove(cardForRemoval);
    }

    public void evaluateTrick(SantaseGame game) {
        SantaseGameState state = game.getState();
        SantaseSeat firstPlayer = game.getFirstPlayer();
        SantaseSeat secondPlayer = game.getSecondPlayer();

        SantaseSeat trickWinner = determineWinner(game);

        // --- Award trick points ---
        int trickPoints = firstPlayer.getPlayedCard().getPoints() + secondPlayer.getPlayedCard().getPoints();
        trickWinner.setScore(trickWinner.getScore() + trickPoints);

        state.setInTurnPlayer(trickWinner);
        state.setFirstTurnPlayer(trickWinner);

        boolean isTrickWinnerBlanked = trickWinner.getIsBlanked();

        if (isTrickWinnerBlanked) {
            trickWinner.setIsBlanked(false);
        }

        // --- Draw cards ---
        drawCards(game, trickWinner);

        // --- End of game scoring ---
        if (isLastCardPlayed(game)) {
            SantaseSeat dealWinner = applyEndOfGameScore(game, trickWinner);
            webSocketUtilService.updateGameState(game, game.getFirstPlayer().getUsername(),
                    dealWinner.getUsername(), game.getFirstPlayer().getScore(), game.getSecondPlayer().getScore());

            webSocketUtilService.updateGameState(game, game.getSecondPlayer().getUsername(),
                    dealWinner.getUsername(), game.getFirstPlayer().getScore(), game.getSecondPlayer().getScore());
            prepareNewState(game, dealWinner);
        }

        // --- Cleanup ---
        firstPlayer.setPlayedCard(null);
        secondPlayer.setPlayedCard(null);
    }

    protected void drawCards(SantaseGame game, SantaseSeat trickWinner) {
        if (game.getState().getDeck().isEmpty()) return;

        trickWinner.drawCard(game.getState().getDeck().removeFirst());
        game.getOpponent(trickWinner).drawCard(game.getState().getDeck().removeFirst());
    }

    public SantaseSeat applyEndOfGameScore(SantaseGame game, SantaseSeat trickWinner) {
        SantaseGameState state = game.getState();

        SantaseSeat dealWinner;
        int bonusPoints;

        if (state.isClosed()) {
            SantaseSeat closer = state.getClosedByPlayer();
            SantaseSeat opponent = game.getOpponent(closer);

            if (closer.getScore() >= 66) {
                // Successful close
                dealWinner = closer;
                bonusPoints = calculateStandardBonus(opponent.getScore(), opponent.getIsBlanked());
            } else {
                // Failed close → penalty
                dealWinner = opponent;
                bonusPoints = 3;
            }

        } else {
            // Open game (normal end)
            dealWinner = trickWinner;
            SantaseSeat loser = game.getOpponent(trickWinner);
            bonusPoints = calculateStandardBonus(loser.getScore(), loser.getIsBlanked());
        }

        dealWinner.setResult(dealWinner.getResult() + bonusPoints);
        return dealWinner;
    }

    private int calculateStandardBonus(int loserScore, boolean loserBlank) {
        if (loserBlank) {
            return 3; // Loser was blanked (had 0 tricks/score)
        } else if (loserScore < 33) {
            return 2; // Loser scored less than 33
        } else {
            return 1; // Loser scored 33 or more
        }
    }

    public void prepareNewState(SantaseGame game, SantaseSeat trickWinner) {
        int firstPlayerResult = game.getFirstPlayer().getResult();
        int secondPlayerResult = game.getSecondPlayer().getResult();

        int difference = Math.abs(firstPlayerResult - secondPlayerResult);

        if ((firstPlayerResult >= 11 || secondPlayerResult >= 11) && difference >= 2) {
            if (firstPlayerResult > secondPlayerResult) {
                finishGame(game, game.getFirstPlayer(), false);
            } else {
                finishGame(game, game.getSecondPlayer(), false);
            }
            log.info(
                    LogConstants.FINISH_GAME,
                    game.getId(),
                    game.getWinner().getUsername(),
                    game.getFirstPlayer().getUsername(),
                    game.getFirstPlayer().getResult(),
                    game.getSecondPlayer().getUsername(),
                    game.getSecondPlayer().getResult()
            );
            return;
        }

        game.getState().setDeck(getNewDeck());

        if (trickWinner == null || trickWinner.equals(game.getSecondPlayer())) {
            game.getState().setFirstTurnPlayer(game.getFirstPlayer());
            game.getState().setInTurnPlayer(game.getFirstPlayer());
        } else {
            game.getState().setFirstTurnPlayer(game.getSecondPlayer());
            game.getState().setInTurnPlayer(game.getSecondPlayer());
        }

        game.getFirstPlayer().setHand(new ArrayList<>());
        game.getSecondPlayer().setHand(new ArrayList<>());
        game.getFirstPlayer().setScore(0);
        game.getSecondPlayer().setScore(0);
        game.getFirstPlayer().setIsBlanked(true);
        game.getSecondPlayer().setIsBlanked(true);
        game.getState().setClosedByPlayer(null);

        // Deal 6 cards each
        for (int i = 0; i < 3; i++) game.getFirstPlayer().getHand().add(game.getState().getDeck().removeFirst());
        for (int i = 0; i < 3; i++) game.getSecondPlayer().getHand().add(game.getState().getDeck().removeFirst());
        for (int i = 0; i < 3; i++) game.getFirstPlayer().getHand().add(game.getState().getDeck().removeFirst());
        for (int i = 0; i < 3; i++) game.getSecondPlayer().getHand().add(game.getState().getDeck().removeFirst());

        game.getState().setTrumpCard(game.getState().getDeck().getLast());
    }

    protected List<Card> getNewDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(Card.builder()
                        .id(UUID.randomUUID())
                        .suit(suit)
                        .rank(rank)
                        .isPlayable(true)
                        .isLastDrawn(false)
                        .build());
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    protected boolean isLastCardPlayed(SantaseGame game) {
        return game.getState().getDeck().isEmpty()
                && game.getFirstPlayer().getHand().isEmpty()
                && game.getSecondPlayer().getHand().isEmpty();
    }

    /**
     * Who takes the trick now on the table. Reads the two cards and changes
     * nothing, so it can be asked before the trick is taken — for the screens
     * to show the winning card while both are still out.
     */
    public SantaseSeat determineWinner(SantaseGame game) {
        SantaseGameState state = game.getState();
        Card firstPlayerCard = game.getFirstPlayer().getPlayedCard();
        Card secondPlayerCard = game.getSecondPlayer().getPlayedCard();

        boolean isFirstPlayerCardTrump = firstPlayerCard.getSuit().equals(state.getTrumpCard().getSuit());
        boolean isSecondPlayerCardTrump = secondPlayerCard.getSuit().equals(state.getTrumpCard().getSuit());

        if (isFirstPlayerCardTrump && !isSecondPlayerCardTrump) return game.getFirstPlayer();
        if (isSecondPlayerCardTrump && !isFirstPlayerCardTrump) return game.getSecondPlayer();

        if (firstPlayerCard.getSuit() == secondPlayerCard.getSuit()) {
            return firstPlayerCard.getPoints() > secondPlayerCard.getPoints()
                    ? game.getFirstPlayer() : game.getSecondPlayer();
        }

        return game.getState().getFirstTurnPlayer();
    }

    public boolean checkTwentyForty(SantaseGame game, SantaseSeat player, Card playedCard) {
        if (game.getState().getDeck().size() == 12) {
            return false;
        }

        // Must play King OR Queen
        Rank played = playedCard.getRank();
        if (played != Rank.KING && played != Rank.QUEEN) {
            return false;
        }

        Suit suit = playedCard.getSuit();
        List<Card> hand = player.getHand();
        Rank partner = played == Rank.KING ? Rank.QUEEN : Rank.KING;

        Card matchingPartner = hand.stream()
                .filter(c -> c.getSuit() == suit && c.getRank() == partner)
                .findFirst()
                .orElse(null);

        if (matchingPartner == null) return false;

        Suit trumpSuit = game.getState().getTrumpCard().getSuit();

        int bonus = (suit == trumpSuit) ? 40 : 20;

        player.setBonus(bonus);
        player.setScore(player.getScore() + bonus);

        hand.forEach(card -> {
            if (!card.equals(playedCard) && !card.equals(matchingPartner)) {
                card.setIsPlayable(false);
            }
        });

        return true;
    }

    /** The game is over: the winner set, and the result written into both records. */
    public void finishGame(SantaseGame game, SantaseSeat winner, boolean opponentSurrendered) {
        santaseTableService.setGameWinner(game, winner, opponentSurrendered);
        santaseStatsService.record(game);
    }

    @Transactional
    public void surrenderByInactivity(UUID gameId) {
        SantaseGame game = santaseTableService.findGameById(gameId)
                .orElseThrow(() -> new NoActiveGameFoundException(gameId.toString()));
        if (game.getWinner() != null) {
            // The game finished normally between the timer firing and this
            // transaction starting. Without this guard setGameWinner would run a
            // second time and double-count the win and the Elo change.
            return;
        }

        SantaseSeat surrenderPlayer = game.getState().getInTurnPlayer();
        SantaseSeat opponentPlayer = game.getOpponent(surrenderPlayer);

        log.info(LogConstants.FINISH_GAME_SURRENDER_INACTIVITY, surrenderPlayer.getUsername(), opponentPlayer.getUsername());

        game.getFirstPlayer().setPlayedCard(null);
        game.getSecondPlayer().setPlayedCard(null);
        game.getFirstPlayer().setHand(new ArrayList<>());
        game.getSecondPlayer().setHand(new ArrayList<>());

        finishGame(game, opponentPlayer, true);
        log.info(
                LogConstants.FINISH_GAME,
                game.getId(),
                opponentPlayer.getUsername(),
                game.getFirstPlayer().getUsername(),
                game.getFirstPlayer().getResult(),
                game.getSecondPlayer().getUsername(),
                game.getSecondPlayer().getResult()
        );

        santaseTableService.saveGame(game);
        webSocketUtilService.updateGameState(game);
    }
}
