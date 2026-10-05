package bg.deck.common.service;

import bg.deck.common.constant.LogConstants;
import bg.deck.common.enums.GameType;
import bg.deck.santase.enums.Rank;
import bg.deck.santase.enums.Suit;
import bg.deck.santase.exception.CardNotFoundException;
import bg.deck.santase.exception.CardNotPlayableException;
import bg.deck.santase.exception.DeckSizeException;
import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.exception.NoActiveGameFoundException;
import bg.deck.santase.exception.NotFirstInTurnException;
import bg.deck.santase.exception.NotInTurnException;
import bg.deck.santase.model.Card;
import bg.deck.common.model.Game;
import bg.deck.santase.model.GameState;
import bg.deck.common.model.Player;
import bg.deck.common.model.User;
import bg.deck.common.model.response.SearchGameResponse;
import bg.deck.common.repository.GameRepository;
import bg.deck.common.repository.GameStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import bg.deck.santase.service.WebSocketUtilService;

import static bg.deck.common.constant.Constants.KING;
import static bg.deck.common.constant.Constants.QUEEN;

@Log4j2
@RequiredArgsConstructor
@Service
public class GameUtilService {
    private final GameRepository gameRepository;
    private final PlayerService playerService;
    private final GameStateRepository gameStateRepository;
    private final UserAccountService userAccountService;
    private final WebSocketUtilService webSocketUtilService;
    private final WebSocketService webSocketService;
    private final RankingService rankingService;

    @Transactional
    public Game startGame(Player firstPlayer, Player secondPlayer) {
        GameState gameState = GameState.builder().build();
        firstPlayer.setResult(0);
        secondPlayer.setResult(0);
        firstPlayer.setInactivityCount(0);
        secondPlayer.setInactivityCount(0);

        Game game = Game.builder()
                .gameType(GameType.SANTASE)
                .firstPlayer(firstPlayer)
                .secondPlayer(secondPlayer)
                .state(gameState)
                .build();

        prepareNewState(game, null);
        return gameRepository.save(game);
    }

    public Game findGameByUsername(String username) {
        return findGameByUsername(username, GameType.SANTASE);
    }

    public Game findGameByUsername(String username, GameType gameType) {
        return gameRepository.findActiveGamesByUsernameAndType(username, gameType)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoActiveGameFoundException(username));
    }

    /** The game with this id, if it is still there. */
    public Optional<Game> findGameById(UUID gameId) {
        return gameRepository.findById(gameId);
    }

    /** Every game still being played. */
    public List<Game> findAllActiveGames() {
        return gameRepository.findAllActive();
    }

    public Game saveGame(Game game) {
        return gameRepository.save(game);
    }

    public void saveGameState(GameState gameState) {
        gameStateRepository.save(gameState);
    }

    public String getUsername() {
        return Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
    }

    public boolean checkIfUserExistsAndIsAvailable(String username) {
        return checkIfUserExistsAndIsAvailable(username, GameType.SANTASE);
    }

    /**
     * True when the user is free to start a game of this type. Scoped by type so
     * a live Santase game no longer blocks a табла search.
     */
    public boolean checkIfUserExistsAndIsAvailable(String username, GameType gameType) {
        Optional<UUID> gameId = userAccountService.findActiveGameId(username, gameType);

        if (gameId.isPresent()) {
            webSocketService.notifyGameSearch(username, gameType, SearchGameResponse.started(gameId.get()));
            return false;
        } else {
            return true;
        }
    }

    /** Creates a fresh seat for a new game. */
    @Transactional
    public Player newPlayerFor(String username) {
        User user = userAccountService.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException(username));
        return playerService.save(Player.builder().user(user).build());
    }

    public Game findGame(String username) {
        Game game = findGameByUsername(username);

        Player player = game.getPlayerByUsername(username);

        GameState state = game.getState();

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

    public void removeCardFromHand(Game game, Player player, Card cardForRemoval) {
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

    public void evaluateTrick(Game game) {
        GameState state = game.getState();
        Player firstPlayer = game.getFirstPlayer();
        Player secondPlayer = game.getSecondPlayer();

        Player trickWinner = determineWinner(game);

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
            Player dealWinner = applyEndOfGameScore(game, trickWinner);
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

    protected void drawCards(Game game, Player trickWinner) {
        if (game.getState().getDeck().isEmpty()) return;

        trickWinner.drawCard(game.getState().getDeck().removeFirst());
        game.getOpponent(trickWinner).drawCard(game.getState().getDeck().removeFirst());
    }

    public Player applyEndOfGameScore(Game game, Player trickWinner) {
        GameState state = game.getState();

        Player dealWinner;
        int bonusPoints;

        if (state.isClosed()) {
            Player closer = state.getClosedByPlayer();
            Player opponent = game.getOpponent(closer);

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
            Player loser = game.getOpponent(trickWinner);
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

    public void prepareNewState(Game game, Player trickWinner) {
        int firstPlayerResult = game.getFirstPlayer().getResult();
        int secondPlayerResult = game.getSecondPlayer().getResult();

        int difference = Math.abs(firstPlayerResult - secondPlayerResult);

        if ((firstPlayerResult >= 11 || secondPlayerResult >= 11) && difference >= 2) {
            if (firstPlayerResult > secondPlayerResult) {
                setGameWinner(game, game.getFirstPlayer(), false);
            } else {
                setGameWinner(game, game.getSecondPlayer(), false);
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

    protected boolean isLastCardPlayed(Game game) {
        return game.getState().getDeck().isEmpty()
                && game.getFirstPlayer().getHand().isEmpty()
                && game.getSecondPlayer().getHand().isEmpty();
    }

    /**
     * Who takes the trick now on the table. Reads the two cards and changes
     * nothing, so it can be asked before the trick is taken — for the screens
     * to show the winning card while both are still out.
     */
    public Player determineWinner(Game game) {
        GameState state = game.getState();
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

    public boolean checkTwentyForty(Game game, Player player, Card playedCard) {
        if (game.getState().getDeck().size() == 12) {
            return false;
        }

        // Must play King OR Queen
        if (!(playedCard.getRank().name().equals(KING) || playedCard.getRank().name().equals(QUEEN))) {
            return false;
        }

        Suit suit = playedCard.getSuit();
        List<Card> hand = player.getHand();

        Card matchingPartner = hand.stream()
                .filter(c -> c.getSuit() == suit &&
                        ((playedCard.getRank().name().equals(KING) && c.getRank().name().equals(QUEEN)) ||
                                (playedCard.getRank().name().equals(QUEEN) && c.getRank().name().equals(KING))))
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

    public void setGameWinner(Game game, Player winner, boolean opponentSurrendered) {
        game.setWinner(winner, opponentSurrendered);
        rankingService.updateRankingAfterGame(game);
    }

    @Transactional
    public void surrenderByInactivity(UUID gameId) {
        Game game = gameRepository.findById(gameId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoActiveGameFoundException(gameId.toString()));
        if (game.getWinner() != null) {
            // The game finished normally between the timer firing and this
            // transaction starting. Without this guard setGameWinner would run a
            // second time and double-count the win and the Elo change.
            return;
        }

        Player surrenderPlayer = game.getState().getInTurnPlayer();
        Player opponentPlayer = game.getOpponent(surrenderPlayer);

        log.info(LogConstants.FINISH_GAME_SURRENDER_INACTIVITY, surrenderPlayer.getUsername(), opponentPlayer.getUsername());

        game.getFirstPlayer().setPlayedCard(null);
        game.getSecondPlayer().setPlayedCard(null);
        game.getFirstPlayer().setHand(new ArrayList<>());
        game.getSecondPlayer().setHand(new ArrayList<>());

        setGameWinner(game, opponentPlayer, true);
        log.info(
                LogConstants.FINISH_GAME,
                game.getId(),
                opponentPlayer.getUsername(),
                game.getFirstPlayer().getUsername(),
                game.getFirstPlayer().getResult(),
                game.getSecondPlayer().getUsername(),
                game.getSecondPlayer().getResult()
        );

        saveGame(game);
        webSocketUtilService.updateGameState(game);
    }
}
