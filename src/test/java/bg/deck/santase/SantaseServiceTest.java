package bg.deck.santase;

import bg.deck.santase.enums.Rank;
import bg.deck.santase.enums.Suit;
import bg.deck.santase.exception.NotInTurnException;
import bg.deck.santase.model.Card;
import bg.deck.common.enums.GameType;
import bg.deck.common.model.Game;
import bg.deck.santase.model.GameState;
import bg.deck.common.model.Player;
import bg.deck.common.model.User;
import bg.deck.santase.model.request.CardRequest;
import bg.deck.common.model.response.SearchGameResponse;
import bg.deck.common.service.AvailabilityService;
import bg.deck.santase.service.SantaseDealService;
import bg.deck.santase.service.SantaseService;
import bg.deck.santase.service.SantaseTurnTimer;
import bg.deck.common.service.GameUtilService;
import bg.deck.common.service.WebSocketService;
import bg.deck.santase.service.WebSocketUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class SantaseServiceTest {

    private final String p1Name = "Alice";
    private final String p2Name = "Bob";
    @Mock
    private WebSocketUtilService webSocketUtilService;
    @Mock
    private WebSocketService webSocketService;
    @Mock
    private GameUtilService gameUtilService;
    @Mock
    private SantaseDealService santaseDealService;
    @Mock
    private SantaseTurnTimer santaseTurnTimer;
    @Mock
    private AvailabilityService availabilityService;
    @InjectMocks
    private SantaseService santaseService;
    private Player p1;
    private Player p2;
    private Game game;
    private GameState state;

    @BeforeEach
    void setUp() {
        p1 = createPlayer(p1Name);
        p2 = createPlayer(p2Name);
        state = GameState.builder()
                .inTurnPlayer(p1)
                .firstTurnPlayer(p1)
                .deck(new ArrayList<>())
                .trumpCard(new Card(UUID.randomUUID(), Suit.HEARTS, Rank.ACE, true, false))
                .build();

        game = Game.builder()
                .firstPlayer(p1)
                .secondPlayer(p2)
                .state(state)
                .build();
    }

    /** The name on the request, as the security context carries it. */
    private static void signIn(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Player createPlayer(String name) {
        User user = new User();
        user.setUsername(name);
        return Player.builder().user(user).hand(new ArrayList<>()).score(0).result(0).build();
    }

    @Nested
    @DisplayName("Matchmaking & Search Tests")
    class SearchTests {
        @Test
        void searchGame_WhenQueueEmpty_AddsUserToQueue() {
            signIn(p1Name);
            when(gameUtilService.checkIfUserExistsAndIsAvailable(p1Name, GameType.SANTASE)).thenReturn(true);

            santaseService.searchGame();

            verify(webSocketService).notifyGameSearch(eq(p1Name), any(SearchGameResponse.class));
            verify(santaseDealService, never()).startGame(any(), any());
        }

        @Test
        void searchGame_WhenPlayerInQueue_StartsNewGame() {
            // First player enters queue
            signIn(p1Name);
            when(gameUtilService.checkIfUserExistsAndIsAvailable(p1Name, GameType.SANTASE)).thenReturn(true);
            santaseService.searchGame();

            // Second player enters queue
            reset(webSocketUtilService);
            signIn(p2Name);
            when(gameUtilService.checkIfUserExistsAndIsAvailable(p2Name, GameType.SANTASE)).thenReturn(true);
            // A fresh seat is now created per game rather than reusing a per-user row.
            when(gameUtilService.newPlayerFor(p2Name)).thenReturn(p2);
            when(gameUtilService.newPlayerFor(p1Name)).thenReturn(p1);
            when(santaseDealService.startGame(p2, p1)).thenReturn(game);

            santaseService.searchGame();

            verify(santaseDealService).startGame(any(), any());
            verify(webSocketService).notifyGameSearch(anyList(), any(SearchGameResponse.class));
        }
    }


    @Nested
    @DisplayName("Gameplay Action Tests")
    class GameplayTests {

        @Test
        void playCard_Success_TurnSwitches() {
            Card card = new Card(UUID.randomUUID(), Suit.CLUBS, Rank.TEN, true, false);
            p1.getHand().add(card);
            CardRequest request = new CardRequest(card.getId());

            signIn(p1Name);
            when(santaseDealService.findGameByUsername(p1Name)).thenReturn(game);

            santaseService.playCard(request);

            assertThat(p1.getPlayedCard()).isEqualTo(card);
            assertThat(state.getInTurnPlayer()).isEqualTo(p2);
            verify(santaseDealService).removeCardFromHand(game, p1, card);
            verify(gameUtilService).saveGame(game);
        }

        @Test
        void playCard_WhenBothPlayed_TriggersEvaluation() {
            Card p2Card = new Card(UUID.randomUUID(), Suit.HEARTS, Rank.KING, true, false);
            p2.setPlayedCard(p2Card); // Bob already played

            Card p1Card = new Card(UUID.randomUUID(), Suit.HEARTS, Rank.TEN, true, false);
            p1.getHand().add(p1Card);

            signIn(p1Name);
            when(santaseDealService.findGameByUsername(p1Name)).thenReturn(game);
            when(santaseDealService.determineWinner(game)).thenReturn(p1); // the ten beats the king

            santaseService.playCard(new CardRequest(p1Card.getId()));

            // Both screens are told whose the trick is while its two cards are
            // still out, and only then is it taken.
            InOrder order = inOrder(webSocketUtilService, santaseDealService);
            order.verify(webSocketUtilService).updateGameStateWithTrickTaker(game, p1Name);
            order.verify(santaseDealService).evaluateTrick(game);
        }

        @Test
        void playCard_WrongTurn_ThrowsException() {
            // 1. Arrange: Setup state and inputs outside the assertion
            state.setInTurnPlayer(p2);
            signIn(p1Name);
            when(santaseDealService.findGameByUsername(p1Name)).thenReturn(game);

            UUID randomId = UUID.randomUUID();
            CardRequest request = new CardRequest(randomId);

            // 2. Act & Assert: Only the specific service call is inside the lambda
            assertThatThrownBy(() -> santaseService.playCard(request))
                    .isInstanceOf(NotInTurnException.class);
        }
    }

    @Nested
    @DisplayName("Marriage & Special Action Tests")
    class SpecialActionTests {

        @Test
        void announceCombination_Success() {
            Card king = new Card(UUID.randomUUID(), Suit.HEARTS, Rank.KING, true, false);
            p1.getHand().add(king);

            signIn(p1Name);
            when(santaseDealService.findGameByUsername(p1Name)).thenReturn(game);
            when(santaseDealService.checkTwentyForty(game, p1, king)).thenReturn(true);

            santaseService.announceCombination(new CardRequest(king.getId()));

            verify(webSocketUtilService).updateGameState(game);
            assertThat(p1.getBonus()).isNull(); // Reset after successful logic
        }

        @Test
        void replaceCard_Success() {
            // 1. Set up the specific cards needed for the Santase "9 of Trumps" replacement rule
            Card aceOfHearts = new Card(UUID.randomUUID(), Suit.HEARTS, Rank.ACE, true, false);
            Card nineOfHearts = new Card(UUID.randomUUID(), Suit.HEARTS, Rank.NINE, true, false);

            // The trump card is the Ace, the player has the Nine
            state.setTrumpCard(aceOfHearts);
            state.getDeck().add(new Card()); // Just to have something in deck
            state.getDeck().add(aceOfHearts); // The bottom card of the deck is the trump

            p1.getHand().add(nineOfHearts);

            // 2. Stub the PUBLIC methods
            signIn(p1Name);
            when(santaseDealService.findGame(p1Name)).thenReturn(game);

            // 3. Execute
            santaseService.replaceCard();

            // 4. Assertions based on the logic in your SantaseService.replaceCard()
            // The trump card in state should now be the Nine
            assertThat(state.getTrumpCard().getRank()).isEqualTo(Rank.NINE);

            // The Nine should have been removed from hand and replaced by the Ace
            assertThat(p1.getHand()).contains(aceOfHearts);
            assertThat(p1.getHand()).doesNotContain(nineOfHearts);

            // Verify persistence
            verify(santaseDealService).saveGameState(state);
            verify(webSocketUtilService).updateGameState(game);
        }
    }

    @Nested
    @DisplayName("End Game Logic Tests")
    class EndGameTests {
        @Test
        void finishDeal_Success() {
            p1.setScore(66);
            p2.setIsBlanked(false);
            p2.setScore(20);

            signIn(p1Name);
            when(santaseDealService.findGameByUsername(p1Name)).thenReturn(game);

            santaseService.finishDeal();

            // P1 wins with 2 Result points because P2 is under 33 but not blanked
            assertThat(p1.getResult()).isEqualTo(2);
            verify(santaseDealService).prepareNewState(game, p1);
            verify(webSocketUtilService).updateGameState(any());
        }

        @Test
        void finishGame_Surrender_OpponentWins() {
            // 1. Setup: P1 is the one surrendering
            signIn(p1Name);
            when(santaseDealService.findGameByUsername(p1Name)).thenReturn(game);
            // Note: We do NOT stub game.getOpponentPlayerByUsername()
            // because it's a real method on a real object.

            // 2. Execute
            santaseService.surrender();

            // 3. Assertions: Check if the real logic worked
            // The winner should be P2 because P1 surrendered (setting it is delegated to GameUtilService)
            verify(gameUtilService).setGameWinner(game, p2, true);

            // Check if hands were cleared as per your service logic
            assertThat(p1.getHand()).isEmpty();
            assertThat(p2.getHand()).isEmpty();

            // Verify interactions
            verify(gameUtilService).saveGame(game);
            verify(webSocketUtilService).updateGameState(game);
        }
    }
}
