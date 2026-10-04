package bg.deck.belot;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.CardPoints;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.Deck;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.DeclarationKind;
import bg.deck.belot.engine.DeclarationScoring;
import bg.deck.belot.engine.Declarations;
import bg.deck.belot.engine.GameScorer;
import bg.deck.belot.engine.LegalMoves;
import bg.deck.belot.engine.Play;
import bg.deck.belot.engine.Rank;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Suit;
import bg.deck.belot.engine.Team;
import bg.deck.belot.engine.Trick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of belot, one test per rule, read straight off
 * {@code docs/belot/RULES.md}.
 *
 * <p>This is the table the engine is built against: a rule that is not here is
 * a rule nobody has agreed on yet. Every question the rules page left open
 * has an answer now, each marked ANSWERED with the number it used to carry,
 * so a variant that plays it differently fails here and nowhere else.
 */
@DisplayName("The rules of belot")
class BelotRulesTableTest {

    @Nested
    @DisplayName("§2 — which card beats which")
    class CardOrder {

        @Test
        @DisplayName("in a trump suit: J 9 A 10 K Q 8 7")
        void trumpOrder() {
            assertEquals(
                    List.of(Rank.JACK, Rank.NINE, Rank.ACE, Rank.TEN, Rank.KING, Rank.QUEEN, Rank.EIGHT, Rank.SEVEN),
                    ranksByStrength(true));
        }

        @Test
        @DisplayName("in a plain suit: A 10 K Q J 9 8 7")
        void plainOrder() {
            assertEquals(
                    List.of(Rank.ACE, Rank.TEN, Rank.KING, Rank.QUEEN, Rank.JACK, Rank.NINE, Rank.EIGHT, Rank.SEVEN),
                    ranksByStrength(false));
        }

        @Test
        @DisplayName("the jack and the nine are only mighty in trumps")
        void theJackIsOnlyMightyInTrumps() {
            assertTrue(Rank.JACK.strength(true) > Rank.ACE.strength(true));
            assertTrue(Rank.JACK.strength(false) < Rank.ACE.strength(false));
            assertTrue(Rank.NINE.strength(true) > Rank.ACE.strength(true));
            assertTrue(Rank.NINE.strength(false) < Rank.JACK.strength(false));
        }

        private static List<Rank> ranksByStrength(boolean trump) {
            return java.util.Arrays.stream(Rank.values())
                    .sorted(Comparator.comparingInt((Rank rank) -> rank.strength(trump)).reversed())
                    .toList();
        }
    }

    @Nested
    @DisplayName("§3 — what cards are worth")
    class Points {

        @ParameterizedTest(name = "{0} in trumps is {1}, in a plain suit {2}")
        @CsvSource({
                "JACK,  20, 2",
                "NINE,  14, 0",
                "ACE,   11, 11",
                "TEN,   10, 10",
                "KING,   4, 4",
                "QUEEN,  3, 3",
                "EIGHT,  0, 0",
                "SEVEN,  0, 0",
        })
        void pointsPerRank(Rank rank, int inTrumps, int inPlain) {
            assertEquals(inTrumps, rank.points(true));
            assertEquals(inPlain, rank.points(false));
        }

        @Test
        @DisplayName("a suit deal is worth 162")
        void suitDealTotal() {
            assertEquals(162, CardPoints.dealTotal(Contract.SPADES));
        }

        @Test
        @DisplayName("every suit contract is worth the same")
        void everySuitIsTheSame() {
            for (Contract contract : List.of(Contract.CLUBS, Contract.DIAMONDS, Contract.HEARTS, Contract.SPADES)) {
                assertEquals(162, CardPoints.dealTotal(contract), contract.name());
            }
        }

        @Test
        @DisplayName("all trumps is worth 258")
        void allTrumpsDealTotal() {
            assertEquals(258, CardPoints.dealTotal(Contract.ALL_TRUMPS));
        }

        @Test
        @DisplayName("no trumps is worth 260: the cards add to 130 and every point counts double")
        void noTrumpsDealTotal() {
            assertEquals(260, CardPoints.dealTotal(Contract.NO_TRUMPS));
            assertEquals(2, CardPoints.multiplier(Contract.NO_TRUMPS));
            assertEquals(1, CardPoints.multiplier(Contract.ALL_TRUMPS), "and nothing else doubles");
            assertEquals(1, CardPoints.multiplier(Contract.SPADES));
        }

        @Test
        @DisplayName("the doubling reaches the last trick too")
        void theLastTrickDoublesAsWell() {
            assertEquals(20, CardPoints.lastTrick(Contract.NO_TRUMPS),
                    "260 only comes out with the ten doubled: 2 x 120 + 2 x 10");
            assertEquals(10, CardPoints.lastTrick(Contract.SPADES));
        }

        @Test
        @DisplayName("an ace is 22 in no trumps, 11 anywhere else")
        void everyCardDoubles() {
            Card ace = new Card(Suit.HEARTS, Rank.ACE);

            assertEquals(22, CardPoints.of(ace, Contract.NO_TRUMPS));
            assertEquals(11, CardPoints.of(ace, Contract.SPADES));
        }
    }

    @Nested
    @DisplayName("§5 — which contract outbids which")
    class Bidding {

        @Test
        @DisplayName("clubs < diamonds < hearts < spades < no trumps < all trumps")
        void biddingOrder() {
            assertEquals(
                    List.of(Contract.CLUBS, Contract.DIAMONDS, Contract.HEARTS,
                            Contract.SPADES, Contract.NO_TRUMPS, Contract.ALL_TRUMPS),
                    List.of(Contract.values()),
                    "OPEN 2 — the rules page prints its suit list with spades twice; this is the usual order");
        }

        @Test
        @DisplayName("a contract beats everything below it and nothing above")
        void beatsIsStrictlyAscending() {
            Contract[] all = Contract.values();
            for (int higher = 0; higher < all.length; higher++) {
                for (int lower = 0; lower < all.length; lower++) {
                    assertEquals(higher > lower, all[higher].beats(all[lower]),
                            all[higher] + " vs " + all[lower]);
                }
            }
        }
    }

    @Nested
    @DisplayName("which suits are trumps")
    class Trumps {

        @ParameterizedTest
        @EnumSource(Suit.class)
        @DisplayName("all trumps: every suit")
        void allTrumps(Suit suit) {
            assertTrue(Contract.ALL_TRUMPS.isTrump(suit));
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        @DisplayName("no trumps: none of them")
        void noTrumps(Suit suit) {
            assertFalse(Contract.NO_TRUMPS.isTrump(suit));
        }

        @Test
        @DisplayName("a suit contract: that one and no other")
        void oneSuit() {
            assertTrue(Contract.HEARTS.isTrump(Suit.HEARTS));
            assertFalse(Contract.HEARTS.isTrump(Suit.SPADES));
            assertFalse(Contract.HEARTS.isTrump(Suit.CLUBS));
            assertFalse(Contract.HEARTS.isTrump(Suit.DIAMONDS));
        }
    }

    @Nested
    @DisplayName("§7 — the order sequences are built from")
    class NaturalOrder {

        @Test
        @DisplayName("7 8 9 10 J Q K A, whatever the contract")
        void naturalOrderIsTheDeclarationOrder() {
            assertEquals(
                    List.of(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
                            Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE),
                    java.util.Arrays.stream(Rank.values())
                            .sorted(java.util.Comparator.comparingInt(Rank::naturalOrder))
                            .toList(),
                    "Declarations reads runs off this order; the enum is declared in it");
        }
    }

    @Nested
    @DisplayName("the deck")
    class TheDeck {

        @Test
        @DisplayName("32 cards, each one once")
        void thirtyTwoDistinctCards() {
            List<Card> deck = Deck.full();

            assertEquals(32, deck.size());
            assertEquals(32, Set.copyOf(deck).size(), "no card appears twice");
        }
    }

    /* ------------- the rules that were open, now answered -------------- */

    @Nested
    @DisplayName("§6 — the obligations, and where they stop")
    class Obligations {

        /**
         * ANSWERED (OPEN 4), and corrected by ANSWERED 12.
         *
         * <p>A partner winning the trick frees you from having to trump when you
         * cannot follow; that is what "ако взятката до момента принадлежи на
         * противника" is the condition of, and it stands. It does not free you
         * from raising when you can follow a suit played by the trump order:
         * that is качване, and it holds whoever is winning. This test used to
         * say the opposite, from reading the one condition as covering both.
         */
        @Test
        @DisplayName("in all trumps, you raise the led suit even over your partner")
        void allTrumpsRaisesEvenOverAPartner() {
            // North leads the ace of spades; South is North's partner.
            Trick trick = new Trick(List.of(
                    new Play(Seat.NORTH, new Card(Suit.SPADES, Rank.ACE))));

            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.JACK),
                    new Card(Suit.SPADES, Rank.SEVEN));

            assertEquals(List.of(new Card(Suit.SPADES, Rank.JACK)),
                    LegalMoves.of(hand, trick, Seat.SOUTH, Contract.ALL_TRUMPS),
                    "the jack beats the ace in all trumps, so the jack, partner or not");
        }

        /** ANSWERED (OPEN 4): an opponent holding it binds you too, of course. */
        @Test
        @DisplayName("and an opponent winning it makes you beat the led suit just the same")
        void allTrumpsObligationWhenAnOpponentIsWinning() {
            Trick trick = new Trick(List.of(
                    new Play(Seat.NORTH, new Card(Suit.SPADES, Rank.ACE))));

            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.JACK),
                    new Card(Suit.SPADES, Rank.SEVEN));

            assertEquals(List.of(new Card(Suit.SPADES, Rank.JACK)),
                    LegalMoves.of(hand, trick, Seat.EAST, Contract.ALL_TRUMPS),
                    "the jack is the one spade that beats the ace in all trumps");
        }

        /** ANSWERED (OPEN 13). */
        @Test
        @DisplayName("an unbeatable trump on the table frees the hand: no trump is wasted")
        void undertrumpingIsNotForced() {
            // Spades are trumps. North led a heart, East trumped with the jack.
            // South holds one spade, the seven, which cannot beat it: "в случай
            // че няма по-висок коз, може да изиграе произволна карта".
            Trick trick = new Trick(List.of(
                    new Play(Seat.NORTH, new Card(Suit.HEARTS, Rank.ACE)),
                    new Play(Seat.EAST, new Card(Suit.SPADES, Rank.JACK))));

            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.SEVEN),
                    new Card(Suit.CLUBS, Rank.ACE));

            assertEquals(hand, LegalMoves.of(hand, trick, Seat.SOUTH, Contract.SPADES),
                    "the seven of trumps is not compulsory on a trick already lost");
        }

        @Test
        @DisplayName("a trump that can beat it, though, is compulsory")
        void overtrumpingIsForced() {
            Trick trick = new Trick(List.of(
                    new Play(Seat.NORTH, new Card(Suit.HEARTS, Rank.ACE)),
                    new Play(Seat.EAST, new Card(Suit.SPADES, Rank.QUEEN))));

            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.KING),
                    new Card(Suit.SPADES, Rank.SEVEN),
                    new Card(Suit.CLUBS, Rank.ACE));

            assertEquals(List.of(new Card(Suit.SPADES, Rank.KING)),
                    LegalMoves.of(hand, trick, Seat.SOUTH, Contract.SPADES),
                    "the king beats the queen, so the king it is");
        }
    }

    @Nested
    @DisplayName("§7 — what is declared, and what it is worth")
    class DeclarationRules {

        /** ANSWERED (OPEN 5), first half: the two kinds are compared apart. */
        @Test
        @DisplayName("fours and sequences are weighed against their own kind, not each other")
        void foursVersusSequences() {
            // Ours: four kings, 100. Theirs: a quinte, also 100. Each side takes
            // what it holds — the four does not beat the sequence out of the
            // scoring, nor the sequence the four.
            List<Declaration> ours = List.of(
                    new Declaration(DeclarationKind.CARRE, null, Rank.KING, 100));
            List<Declaration> theirs = List.of(
                    new Declaration(DeclarationKind.QUINTE, Suit.HEARTS, Rank.ACE, 100));

            assertEquals(100, DeclarationScoring.scoreFor(ours, theirs), "our four still scores");
            assertEquals(100, DeclarationScoring.scoreFor(theirs, ours), "and their quinte still scores");
        }

        /** ANSWERED (OPEN 5), second half: one card, one combination. */
        @Test
        @DisplayName("a card serves one combination only, and the hand keeps the better")
        void aCardCountsOnce() {
            // Four nines (150) and 7 8 9 of spades (20) share the nine of spades:
            // "играчът избира кое от двете да обяви", and the four is worth more.
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.NINE),
                    new Card(Suit.HEARTS, Rank.NINE),
                    new Card(Suit.DIAMONDS, Rank.NINE),
                    new Card(Suit.CLUBS, Rank.NINE),
                    new Card(Suit.SPADES, Rank.SEVEN),
                    new Card(Suit.SPADES, Rank.EIGHT));

            List<Declaration> held = Declarations.in(hand, Contract.HEARTS);

            assertEquals(1, held.size(), "one of the two, not both: " + held);
            assertEquals(DeclarationKind.CARRE, held.getFirst().kind());
            assertEquals(150, held.getFirst().points());
        }

        @Test
        @DisplayName("and both stand when they share nothing")
        void bothStandWhenTheyDoNotOverlap() {
            // Four nines, and J Q K of spades. The run stops short of the ten,
            // so the nine of spades is not part of it and both hold up.
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.NINE),
                    new Card(Suit.HEARTS, Rank.NINE),
                    new Card(Suit.DIAMONDS, Rank.NINE),
                    new Card(Suit.CLUBS, Rank.NINE),
                    new Card(Suit.SPADES, Rank.JACK),
                    new Card(Suit.SPADES, Rank.QUEEN),
                    new Card(Suit.SPADES, Rank.KING));

            List<Declaration> held = Declarations.in(hand, Contract.HEARTS);

            assertEquals(170, held.stream().mapToInt(Declaration::points).sum(),
                    "150 for the nines and 20 for the terz: " + held);
        }

        @Test
        @DisplayName("what a four leaves of a sequence is read again")
        void theRestOfASequenceStillCounts() {
            // Four tens, and 7 8 9 10 of spades. The ten of spades goes to the
            // four; 7 8 9 is still three in a row and is still a terz.
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.TEN),
                    new Card(Suit.HEARTS, Rank.TEN),
                    new Card(Suit.DIAMONDS, Rank.TEN),
                    new Card(Suit.CLUBS, Rank.TEN),
                    new Card(Suit.SPADES, Rank.SEVEN),
                    new Card(Suit.SPADES, Rank.EIGHT),
                    new Card(Suit.SPADES, Rank.NINE));

            List<Declaration> held = Declarations.in(hand, Contract.HEARTS);

            assertEquals(List.of(DeclarationKind.CARRE, DeclarationKind.TERZ),
                    held.stream().map(Declaration::kind).toList(), "the four and a terz: " + held);
            assertEquals(Rank.NINE, held.get(1).topRank(), "7 8 9, without the ten");
        }

        @Test
        @DisplayName("a four and a 50 on the same card: the four, which is worth more")
        void theFourBeatsTheQuarteItShares() {
            // Four tens, and 8 9 10 J of spades: 100 against 50, one of the two.
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.TEN),
                    new Card(Suit.HEARTS, Rank.TEN),
                    new Card(Suit.DIAMONDS, Rank.TEN),
                    new Card(Suit.CLUBS, Rank.TEN),
                    new Card(Suit.SPADES, Rank.EIGHT),
                    new Card(Suit.SPADES, Rank.NINE),
                    new Card(Suit.SPADES, Rank.JACK));

            List<Declaration> held = Declarations.in(hand, Contract.HEARTS);

            assertEquals(List.of(DeclarationKind.CARRE), held.stream().map(Declaration::kind).toList(),
                    "never both: " + held);
        }

        @Test
        @DisplayName("the split worth most is chosen, even when the bigger sequence has to go")
        void theRichestSplitIsChosen() {
            // Four tens, and 9 10 J Q K of spades. The quinte alone is 100; the
            // four (100) leaves J Q K, a terz: 120, so that is the choice.
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.TEN),
                    new Card(Suit.HEARTS, Rank.TEN),
                    new Card(Suit.DIAMONDS, Rank.TEN),
                    new Card(Suit.CLUBS, Rank.TEN),
                    new Card(Suit.SPADES, Rank.NINE),
                    new Card(Suit.SPADES, Rank.JACK),
                    new Card(Suit.SPADES, Rank.QUEEN),
                    new Card(Suit.SPADES, Rank.KING));

            List<Declaration> held = Declarations.in(hand, Contract.HEARTS);

            assertEquals(120, held.stream().mapToInt(Declaration::points).sum(), "100 and 20: " + held);
        }

        @Test
        @DisplayName("a king and queen in a four are still a belote")
        void aBeloteInsideAFour() {
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.KING),
                    new Card(Suit.HEARTS, Rank.KING),
                    new Card(Suit.DIAMONDS, Rank.KING),
                    new Card(Suit.CLUBS, Rank.KING),
                    new Card(Suit.HEARTS, Rank.QUEEN));

            List<DeclarationKind> held = Declarations.in(hand, Contract.HEARTS).stream()
                    .map(Declaration::kind).toList();

            assertEquals(List.of(DeclarationKind.CARRE, DeclarationKind.BELOTE), held);
        }

        @Test
        @DisplayName("two terzes in two suits both count")
        void twoTerzesBothCount() {
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.SEVEN),
                    new Card(Suit.SPADES, Rank.EIGHT),
                    new Card(Suit.SPADES, Rank.NINE),
                    new Card(Suit.HEARTS, Rank.SEVEN),
                    new Card(Suit.HEARTS, Rank.EIGHT),
                    new Card(Suit.HEARTS, Rank.NINE));

            assertEquals(40, Declarations.in(hand, Contract.CLUBS).stream()
                    .mapToInt(Declaration::points).sum(), "20 and 20");
        }

        /** ANSWERED (OPEN 6). */
        @Test
        @DisplayName("a belote scores whoever holds the best sequence")
        void beloteIsIndependent() {
            List<Declaration> ours = List.of(
                    new Declaration(DeclarationKind.TERZ, Suit.SPADES, Rank.NINE, 20),
                    new Declaration(DeclarationKind.BELOTE, Suit.HEARTS, Rank.KING, 20));
            List<Declaration> theirs = List.of(
                    new Declaration(DeclarationKind.QUINTE, Suit.CLUBS, Rank.ACE, 100));

            assertEquals(20, DeclarationScoring.scoreFor(ours, theirs),
                    "the terz is beaten and scores nothing; the belote is not in that contest");
        }

        /** ANSWERED (OPEN 7). */
        @ParameterizedTest(name = "four {0}s beats four {1}s")
        @CsvSource({
                "JACK, NINE",
                "NINE, ACE",
                "ACE, TEN",
                "TEN, KING",
                "KING, QUEEN",
        })
        @DisplayName("fours rank J > 9 > A > 10 > K > Q")
        void whichFourWins(Rank better, Rank worse) {
            List<Declaration> ours = List.of(four(better));
            List<Declaration> theirs = List.of(four(worse));

            assertTrue(DeclarationScoring.scoreFor(ours, theirs) > 0,
                    "four " + better + "s is the better holding");
            assertEquals(0, DeclarationScoring.scoreFor(theirs, ours),
                    "four " + worse + "s is beaten by it");
        }

        /** ANSWERED (OPEN 8). */
        @Test
        @DisplayName("declarations stand in a suit contract, and only no trumps forbids them")
        void declarationsInASuitContract() {
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.SEVEN),
                    new Card(Suit.SPADES, Rank.EIGHT),
                    new Card(Suit.SPADES, Rank.NINE));

            assertFalse(Declarations.in(hand, Contract.HEARTS).isEmpty(), "a suit contract has them");
            assertFalse(Declarations.in(hand, Contract.ALL_TRUMPS).isEmpty(), "all trumps too");
            assertTrue(Declarations.in(hand, Contract.NO_TRUMPS).isEmpty(),
                    "играчите нямат право да обявяват притежаваните от тях комбинации");
        }

        /** ANSWERED (OPEN 14). */
        @ParameterizedTest(name = "{0} in a row is worth {1}")
        @CsvSource({"3, 20", "4, 50", "5, 100", "6, 100", "7, 100", "8, 100"})
        @DisplayName("a run of five or more is a quinte, however long it runs")
        void longRuns(int length, int expected) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                hand.add(new Card(Suit.SPADES, Rank.values()[i]));
            }

            List<Declaration> held = Declarations.in(hand, Contract.HEARTS);

            assertEquals(1, held.size(), "one run, not several: " + held);
            assertEquals(expected, held.getFirst().points());
        }

        /** ANSWERED (OPEN 15). */
        @Test
        @DisplayName("in all trumps a belote is held in every suit that has the king and queen")
        void belotesInAllTrumps() {
            List<Card> hand = List.of(
                    new Card(Suit.SPADES, Rank.KING), new Card(Suit.SPADES, Rank.QUEEN),
                    new Card(Suit.HEARTS, Rank.KING), new Card(Suit.HEARTS, Rank.QUEEN),
                    new Card(Suit.CLUBS, Rank.KING), new Card(Suit.CLUBS, Rank.QUEEN));

            assertEquals(3, belotesIn(hand, Contract.ALL_TRUMPS),
                    "в игра всичко коз белот може да се обяви във всяка боя");
            assertEquals(1, belotesIn(hand, Contract.HEARTS),
                    "in a suit contract only the trump suit has one");
        }

        private static long belotesIn(List<Card> hand, Contract contract) {
            return Declarations.in(hand, contract).stream()
                    .filter(held -> held.kind() == DeclarationKind.BELOTE)
                    .count();
        }

        private static Declaration four(Rank rank) {
            int points = switch (rank) {
                case JACK -> 200;
                case NINE -> 150;
                default -> 100;
            };
            return new Declaration(DeclarationKind.CARRE, null, rank, points);
        }
    }

    @Nested
    @DisplayName("§9 — crossing the line")
    class TheLine {

        /** ANSWERED (OPEN 10). */
        @Test
        @DisplayName("both teams over 151: the higher total takes it")
        void bothTeamsCrossTheLine() {
            assertEquals(Team.NORTH_SOUTH,
                    GameScorer.verdict(162, 155, false).winningTeam().orElseThrow(),
                    "печели този от тях, който има повече точки");
        }

        /** ANSWERED (OPEN 10), the tie. */
        @Test
        @DisplayName("level on the line, another deal is played rather than a draw")
        void levelOnTheLine() {
            assertFalse(GameScorer.verdict(155, 155, false).isFinished());
        }
    }
}
