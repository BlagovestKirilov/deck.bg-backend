package bg.deck.belot;

import bg.deck.belot.engine.Card;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.enums.DeclarationKind;
import bg.deck.belot.engine.DeclarationScoring;
import bg.deck.belot.engine.Declarations;
import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RULES §7 — what a hand may declare, and whose declarations count.
 */
@DisplayName("Declarations")
class BelotDeclarationsTest {

    private static Card card(Rank rank, Suit suit) {
        return new Card(suit, rank);
    }

    private static List<Card> hand(Card... cards) {
        return List.of(cards);
    }

    @Nested
    @DisplayName("found in a hand")
    class Found {

        @Test
        @DisplayName("three in a row is a terz, worth 20")
        void terz() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.NINE, Suit.HEARTS), card(Rank.TEN, Suit.HEARTS), card(Rank.JACK, Suit.HEARTS),
                            card(Rank.ACE, Suit.SPADES)),
                    Contract.SPADES);

            assertEquals(1, found.size());
            assertEquals(DeclarationKind.TERZ, found.getFirst().kind());
            assertEquals(20, found.getFirst().points());
            assertEquals(Rank.JACK, found.getFirst().topRank(), "the run's top card, 9-10-J");
        }

        @Test
        @DisplayName("sequences run by the natural order, so the jack sits under the queen")
        void sequencesUseTheNaturalOrder() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.JACK, Suit.HEARTS), card(Rank.QUEEN, Suit.HEARTS), card(Rank.KING, Suit.HEARTS)),
                    Contract.HEARTS);

            assertEquals(DeclarationKind.TERZ, found.getFirst().kind(),
                    "J-Q-K is a run even though the jack outranks them both in play");
        }

        @Test
        @DisplayName("four in a row is one quarte, not two terzes")
        void quarteNotTwoTerzes() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.EIGHT, Suit.CLUBS), card(Rank.NINE, Suit.CLUBS),
                            card(Rank.TEN, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS)),
                    Contract.SPADES);

            assertEquals(1, found.size());
            assertEquals(DeclarationKind.QUARTE, found.getFirst().kind());
            assertEquals(50, found.getFirst().points());
        }

        @Test
        @DisplayName("five or more is a quinte, worth 100")
        void quinte() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.TEN, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS), card(Rank.QUEEN, Suit.CLUBS),
                            card(Rank.KING, Suit.CLUBS), card(Rank.ACE, Suit.CLUBS)),
                    Contract.SPADES);

            assertEquals(DeclarationKind.QUINTE, found.getFirst().kind());
            assertEquals(100, found.getFirst().points());
        }

        @Test
        @DisplayName("two runs in different suits are both declared")
        void twoSuitsTwoRuns() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.SEVEN, Suit.HEARTS), card(Rank.EIGHT, Suit.HEARTS), card(Rank.NINE, Suit.HEARTS),
                            card(Rank.QUEEN, Suit.CLUBS), card(Rank.KING, Suit.CLUBS), card(Rank.ACE, Suit.CLUBS)),
                    Contract.SPADES);

            assertEquals(2, found.size());
            assertTrue(found.stream().allMatch(declaration -> declaration.kind() == DeclarationKind.TERZ));
        }

        @Test
        @DisplayName("a gap breaks the run")
        void aGapBreaksIt() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.SEVEN, Suit.HEARTS), card(Rank.EIGHT, Suit.HEARTS), card(Rank.TEN, Suit.HEARTS)),
                    Contract.SPADES);

            assertEquals(List.of(), found, "7-8 then a missing nine is nothing");
        }

        @Test
        @DisplayName("four jacks 200, four nines 150, the rest 100")
        void carres() {
            assertEquals(200, carreOf(Rank.JACK));
            assertEquals(150, carreOf(Rank.NINE));
            assertEquals(100, carreOf(Rank.ACE));
            assertEquals(100, carreOf(Rank.TEN));
            assertEquals(100, carreOf(Rank.KING));
            assertEquals(100, carreOf(Rank.QUEEN));
        }

        @Test
        @DisplayName("four sevens and four eights are worth nothing")
        void lowCarresAreNotDeclarations() {
            assertEquals(List.of(), Declarations.in(fourOf(Rank.SEVEN), Contract.SPADES));
            assertEquals(List.of(), Declarations.in(fourOf(Rank.EIGHT), Contract.SPADES));
        }

        @Test
        @DisplayName("king and queen of the trump suit is a belote, worth 20")
        void belote() {
            List<Declaration> found = Declarations.in(
                    hand(card(Rank.KING, Suit.SPADES), card(Rank.QUEEN, Suit.SPADES)),
                    Contract.SPADES);

            assertEquals(1, found.size());
            assertEquals(DeclarationKind.BELOTE, found.getFirst().kind());
            assertEquals(20, found.getFirst().points());
        }

        @Test
        @DisplayName("king and queen of a plain suit is not")
        void noBeloteOutsideTrumps() {
            assertEquals(List.of(), Declarations.in(
                    hand(card(Rank.KING, Suit.HEARTS), card(Rank.QUEEN, Suit.HEARTS)),
                    Contract.SPADES));
        }

        @Test
        @DisplayName("nothing at all in no trumps")
        void noTrumpsForbidsThem() {
            List<Card> rich = hand(
                    card(Rank.NINE, Suit.HEARTS), card(Rank.TEN, Suit.HEARTS), card(Rank.JACK, Suit.HEARTS),
                    card(Rank.KING, Suit.SPADES), card(Rank.QUEEN, Suit.SPADES));

            assertEquals(List.of(), Declarations.in(rich, Contract.NO_TRUMPS),
                    "«играчите нямат право да обявяват притежаваните от тях комбинации»");
        }

        private static int carreOf(Rank rank) {
            List<Declaration> found = Declarations.in(fourOf(rank), Contract.SPADES);
            return found.isEmpty() ? 0 : found.getFirst().points();
        }

        private static List<Card> fourOf(Rank rank) {
            return List.of(card(rank, Suit.CLUBS), card(rank, Suit.DIAMONDS),
                    card(rank, Suit.HEARTS), card(rank, Suit.SPADES));
        }
    }

    @Nested
    @DisplayName("counted between the teams")
    class Contest {

        private final Declaration terzToJack =
                new Declaration(DeclarationKind.TERZ, Suit.HEARTS, Rank.JACK, 20);
        private final Declaration terzToAce =
                new Declaration(DeclarationKind.TERZ, Suit.CLUBS, Rank.ACE, 20);
        private final Declaration quarte =
                new Declaration(DeclarationKind.QUARTE, Suit.SPADES, Rank.JACK, 50);

        @Test
        @DisplayName("the longer sequence takes it, and the loser scores none")
        void longerWins() {
            assertEquals(50, DeclarationScoring.scoreFor(List.of(quarte), List.of(terzToAce)));
            assertEquals(0, DeclarationScoring.scoreFor(List.of(terzToAce), List.of(quarte)));
        }

        @Test
        @DisplayName("equal length is settled by the top card")
        void equalLengthGoesToTheHigherCard() {
            assertEquals(20, DeclarationScoring.scoreFor(List.of(terzToAce), List.of(terzToJack)));
            assertEquals(0, DeclarationScoring.scoreFor(List.of(terzToJack), List.of(terzToAce)));
        }

        @Test
        @DisplayName("equal in both, and every sequence on the table is cancelled")
        void equalCancelsBoth() {
            Declaration mirror = new Declaration(DeclarationKind.TERZ, Suit.SPADES, Rank.JACK, 20);

            assertEquals(0, DeclarationScoring.scoreFor(List.of(terzToJack), List.of(mirror)));
            assertEquals(0, DeclarationScoring.scoreFor(List.of(mirror), List.of(terzToJack)));
        }

        @Test
        @DisplayName("the winning team scores all of its sequences, not just the best")
        void theWinnerScoresEverything() {
            assertEquals(70, DeclarationScoring.scoreFor(List.of(quarte, terzToAce), List.of(terzToJack)));
        }

        @Test
        @DisplayName("unanswered sequences simply score")
        void nobodyToBeat() {
            assertEquals(20, DeclarationScoring.scoreFor(List.of(terzToJack), List.of()));
        }

        @Test
        @DisplayName("OPEN 5 — carrés are compared among themselves, not against sequences")
        void openFiveCarresAreTheirOwnContest() {
            Declaration carreOfNines = new Declaration(DeclarationKind.CARRE, null, Rank.NINE, 150);

            assertEquals(150, DeclarationScoring.scoreFor(List.of(carreOfNines), List.of(quarte)),
                    "assumed: a quarte does not beat a carré, they are separate contests");
        }

        @Test
        @DisplayName("OPEN 7 — four jacks beat four nines")
        void openSevenCarreOrder() {
            Declaration jacks = new Declaration(DeclarationKind.CARRE, null, Rank.JACK, 200);
            Declaration nines = new Declaration(DeclarationKind.CARRE, null, Rank.NINE, 150);

            assertEquals(200, DeclarationScoring.scoreFor(List.of(jacks), List.of(nines)));
            assertEquals(0, DeclarationScoring.scoreFor(List.of(nines), List.of(jacks)),
                    "assumed: carrés rank by the trump order, J 9 A 10 K Q");
        }

        @Test
        @DisplayName("OPEN 6 — a belote scores whatever the other team holds")
        void openSixBeloteIsIndependent() {
            Declaration belote = new Declaration(DeclarationKind.BELOTE, Suit.SPADES, Rank.KING, 20);

            assertEquals(20, DeclarationScoring.scoreFor(List.of(belote), List.of(quarte)),
                    "assumed: the belote is not part of the sequence contest");
        }
    }
}
