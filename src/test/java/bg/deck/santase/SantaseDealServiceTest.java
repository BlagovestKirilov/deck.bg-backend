package bg.deck.santase;

import bg.deck.santase.enums.Rank;
import bg.deck.santase.enums.Suit;
import bg.deck.santase.exception.CardNotPlayableException;
import bg.deck.santase.model.Card;
import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseGameState;
import bg.deck.santase.model.SantaseSeat;
import bg.deck.santase.service.SantaseDealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SantaseDealServiceTest {

    @InjectMocks
    private SantaseDealService santaseDealService;

    private SantaseSeat p1;
    private SantaseSeat p2;
    private SantaseGame game;
    private SantaseGameState state;

    @BeforeEach
    void setUp() {
        p1 = createPlayer("p1");
        p2 = createPlayer("p2");
        state = SantaseGameState.builder().deck(new LinkedList<>()).build();
        game = SantaseGame.builder().firstPlayer(p1).secondPlayer(p2).state(state).build();
    }

    private SantaseSeat createPlayer(String name) {
        return SantaseSeat.builder().username(name).hand(new ArrayList<>()).score(0).result(0).isBlanked(true).build();
    }

    @Nested
    @DisplayName("Card Rules (Santase 66) Tests")
    class RuleTests {

        @Test
        @DisplayName("Must Follow Suit: Throw Exception when player has suit but plays different")
        void removeCardFromHand_MustFollowSuit_Failure() {
            state.getDeck().clear(); // Rule applies when deck is empty
            state.setFirstTurnPlayer(p2);
            state.setTrumpCard(Card.builder().suit(Suit.HEARTS).build());

            // P2 plays Spades
            p2.setPlayedCard(Card.builder().suit(Suit.SPADES).rank(Rank.ACE).build());

            // P1 has Spades but tries to play Clubs
            Card p1Spade = Card.builder().id(UUID.randomUUID()).suit(Suit.SPADES).rank(Rank.NINE).build();
            Card p1Club = Card.builder().id(UUID.randomUUID()).suit(Suit.CLUBS).rank(Rank.TEN).build();
            p1.getHand().addAll(List.of(p1Spade, p1Club));

            assertThatThrownBy(() -> santaseDealService.removeCardFromHand(game, p1, p1Club))
                    .isInstanceOf(CardNotPlayableException.class);
        }

        @Test
        @DisplayName("Trump Rule: Must play Trump if no matching suit available")
        void removeCardFromHand_MustPlayTrump_Failure() {
            state.getDeck().clear();
            state.setFirstTurnPlayer(p2);
            state.setTrumpCard(Card.builder().suit(Suit.HEARTS).build());

            p2.setPlayedCard(Card.builder().suit(Suit.SPADES).rank(Rank.ACE).build());

            // P1 has no Spades but has Hearts (Trump)
            Card p1Trump = Card.builder().id(UUID.randomUUID()).suit(Suit.HEARTS).rank(Rank.NINE).build();
            Card p1Club = Card.builder().id(UUID.randomUUID()).suit(Suit.CLUBS).rank(Rank.TEN).build();
            p1.getHand().addAll(List.of(p1Trump, p1Club));

            assertThatThrownBy(() -> santaseDealService.removeCardFromHand(game, p1, p1Club))
                    .isInstanceOf(CardNotPlayableException.class);
        }
    }


    @Nested
    @DisplayName("Marriage (20/40) Logic")
    class MarriageTests {

        @Test
        void checkTwentyForty_Success_AddsScore() {
            state.setDeck(new ArrayList<>(Collections.nCopies(10, new Card())));
            state.setTrumpCard(Card.builder().suit(Suit.CLUBS).build());

            Card king = Card.builder().suit(Suit.CLUBS).rank(Rank.KING).build();
            Card queen = Card.builder().suit(Suit.CLUBS).rank(Rank.QUEEN).build();
            p1.getHand().addAll(List.of(king, queen));

            boolean result = santaseDealService.checkTwentyForty(game, p1, king);

            assertThat(result).isTrue();
            assertThat(p1.getScore()).isEqualTo(40);
            assertThat(p1.getBonus()).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("Game End Scoring")
    class ScoringTests {

        @Test
        void applyEndOfGameScore_NormalWin_LoserUnder33_Awards2Points() {
            p1.setScore(70); // Winner
            p2.setScore(20); // Loser < 33
            p2.setIsBlanked(false);
            state.setClosedByPlayer(null);

            santaseDealService.applyEndOfGameScore(game, p1);

            assertThat(p1.getResult()).isEqualTo(2);
        }

        @Test
        void applyEndOfGameScore_NormalWin_LoserBlanked_Awards3Points() {
            p1.setScore(66);
            p2.setScore(0);
            p2.setIsBlanked(true);

            santaseDealService.applyEndOfGameScore(game, p1);

            assertThat(p1.getResult()).isEqualTo(3);
        }
    }
}
