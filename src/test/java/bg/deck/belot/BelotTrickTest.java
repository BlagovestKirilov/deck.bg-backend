package bg.deck.belot;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.LegalMoves;
import bg.deck.belot.engine.Play;
import bg.deck.belot.engine.Rank;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Suit;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.engine.TrickResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RULES §6 — who takes a trick, and what a seat is allowed to play into it.
 *
 * <p>Written as the situations they are: someone leads, someone is winning, and
 * the player to move either holds the suit or does not.
 */
@DisplayName("A trick")
class BelotTrickTest {

    private static Card card(Rank rank, Suit suit) {
        return new Card(suit, rank);
    }

    private static Trick trick(Play... plays) {
        Trick trick = Trick.empty();
        for (Play play : plays) {
            trick = trick.with(play);
        }
        return trick;
    }

    private static Play play(Seat seat, Rank rank, Suit suit) {
        return new Play(seat, card(rank, suit));
    }

    @Nested
    @DisplayName("is taken by")
    class TakenBy {

        @Test
        @DisplayName("the highest card of the led suit, when nobody trumps")
        void highestOfTheLedSuit() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.KING, Suit.HEARTS),
                    play(Seat.WEST, Rank.ACE, Suit.HEARTS),
                    play(Seat.SOUTH, Rank.JACK, Suit.HEARTS));

            assertEquals(Seat.WEST, TrickResolver.leader(played, Contract.SPADES).orElseThrow(),
                    "the ace: in a plain suit the jack is worth little");
        }

        @Test
        @DisplayName("a trump, however small, over any plain card")
        void anyTrumpBeatsAPlainCard() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.ACE, Suit.HEARTS),
                    play(Seat.WEST, Rank.SEVEN, Suit.SPADES));

            assertEquals(Seat.WEST, TrickResolver.leader(played, Contract.SPADES).orElseThrow());
        }

        @Test
        @DisplayName("the higher trump, when two are played")
        void higherTrumpWins() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.ACE, Suit.HEARTS),
                    play(Seat.WEST, Rank.ACE, Suit.SPADES),
                    play(Seat.SOUTH, Rank.NINE, Suit.SPADES));

            assertEquals(Seat.SOUTH, TrickResolver.leader(played, Contract.SPADES).orElseThrow(),
                    "the nine of trumps beats the ace of trumps");
        }

        @Test
        @DisplayName("a card of another suit never, even in all trumps")
        void anotherSuitCannotWinInAllTrumps() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.SEVEN, Suit.HEARTS),
                    play(Seat.WEST, Rank.JACK, Suit.SPADES));

            assertEquals(Seat.NORTH, TrickResolver.leader(played, Contract.ALL_TRUMPS).orElseThrow(),
                    "all trumps has no trump suit: only the led suit can win");
        }

        @Test
        @DisplayName("the jack, in all trumps, within the led suit")
        void jackIsHighestInAllTrumps() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.ACE, Suit.HEARTS),
                    play(Seat.WEST, Rank.JACK, Suit.HEARTS));

            assertEquals(Seat.WEST, TrickResolver.leader(played, Contract.ALL_TRUMPS).orElseThrow());
        }
    }

    @Nested
    @DisplayName("may be played into with")
    class Legality {

        private final List<Card> hand = List.of(
                card(Rank.KING, Suit.HEARTS),
                card(Rank.SEVEN, Suit.HEARTS),
                card(Rank.NINE, Suit.SPADES),
                card(Rank.QUEEN, Suit.SPADES),
                card(Rank.ACE, Suit.CLUBS));

        @Test
        @DisplayName("anything, when leading")
        void theLeaderIsFree() {
            assertEquals(hand, LegalMoves.of(hand, Trick.empty(), Seat.SOUTH, Contract.SPADES));
        }

        @Test
        @DisplayName("the led suit, when you hold it")
        void mustFollowSuit() {
            Trick played = trick(play(Seat.NORTH, Rank.ACE, Suit.HEARTS));

            assertEquals(
                    List.of(card(Rank.KING, Suit.HEARTS), card(Rank.SEVEN, Suit.HEARTS)),
                    LegalMoves.of(hand, played, Seat.WEST, Contract.SPADES));
        }

        @Test
        @DisplayName("a trump, when void and an opponent holds the trick")
        void mustTrumpForAnOpponent() {
            Trick played = trick(play(Seat.NORTH, Rank.ACE, Suit.DIAMONDS));

            assertEquals(
                    List.of(card(Rank.NINE, Suit.SPADES), card(Rank.QUEEN, Suit.SPADES)),
                    LegalMoves.of(hand, played, Seat.WEST, Contract.SPADES),
                    "north is west's opponent, so the spades are compulsory");
        }

        @Test
        @DisplayName("anything, when void and your own partner holds the trick")
        void partnerWinningFreesYou() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.SEVEN, Suit.DIAMONDS),
                    play(Seat.WEST, Rank.ACE, Suit.DIAMONDS));

            assertEquals(hand, LegalMoves.of(hand, played, Seat.EAST, Contract.SPADES),
                    "west is east's partner: 'ако взятката принадлежи на противника' does not apply");
        }

        @Test
        @DisplayName("anything, when void with no trump in hand")
        void noTrumpsToPlay() {
            List<Card> trumpless = List.of(card(Rank.KING, Suit.HEARTS), card(Rank.ACE, Suit.CLUBS));
            Trick played = trick(play(Seat.NORTH, Rank.ACE, Suit.DIAMONDS));

            assertEquals(trumpless, LegalMoves.of(trumpless, played, Seat.WEST, Contract.SPADES));
        }

        @Test
        @DisplayName("only a higher trump, when an opponent has already trumped")
        void mustOvertrump() {
            Trick played = trick(
                    play(Seat.NORTH, Rank.ACE, Suit.DIAMONDS),
                    play(Seat.WEST, Rank.SEVEN, Suit.DIAMONDS),
                    play(Seat.SOUTH, Rank.QUEEN, Suit.SPADES));

            assertEquals(
                    List.of(card(Rank.NINE, Suit.SPADES)),
                    LegalMoves.of(hand, played, Seat.EAST, Contract.SPADES),
                    "south is east's opponent: the queen of trumps is on the table "
                            + "and only the nine beats it");
        }

        @Test
        @DisplayName("anything, when void in no trumps — there is nothing to trump with")
        void noTrumpsContractHasNothingToTrumpWith() {
            Trick played = trick(play(Seat.NORTH, Rank.ACE, Suit.DIAMONDS));

            assertEquals(hand, LegalMoves.of(hand, played, Seat.WEST, Contract.NO_TRUMPS));
        }

        @Test
        @DisplayName("anything, when void in all trumps — likewise")
        void allTrumpsHasNoTrumpSuitEither() {
            Trick played = trick(play(Seat.NORTH, Rank.ACE, Suit.DIAMONDS));

            assertEquals(hand, LegalMoves.of(hand, played, Seat.WEST, Contract.ALL_TRUMPS));
        }
    }

    @Nested
    @DisplayName("raising in trumps, and when you need not")
    class Raising {

        /**
         * ANSWERED (OPEN 12) — качване.
         *
         * <p>When trumps are led you must go higher if you can. The rules page
         * never said so; the answer came from the table, which is where the
         * rule is played.
         */
        @Test
        @DisplayName("following a led trump, you must beat what is there")
        void aLedTrumpIsRaised() {
            List<Card> hand = List.of(
                    card(Rank.NINE, Suit.SPADES),
                    card(Rank.SEVEN, Suit.SPADES),
                    card(Rank.ACE, Suit.HEARTS));
            Trick played = trick(play(Seat.NORTH, Rank.QUEEN, Suit.SPADES));

            assertEquals(
                    List.of(card(Rank.NINE, Suit.SPADES)),
                    LegalMoves.of(hand, played, Seat.WEST, Contract.SPADES),
                    "the seven is not allowed while the nine beats the queen");
        }

        @Test
        @DisplayName("and your partner's trump is raised just as an opponent's is")
        void aPartnersTrumpIsRaisedToo() {
            // The hand that found it: partner leads the queen of trumps, you hold
            // the jack, the ace, the king and the seven, and the seven was on
            // offer because the trick was your own side's.
            List<Card> hand = List.of(
                    card(Rank.JACK, Suit.SPADES),
                    card(Rank.ACE, Suit.SPADES),
                    card(Rank.KING, Suit.SPADES),
                    card(Rank.SEVEN, Suit.SPADES),
                    card(Rank.KING, Suit.HEARTS));
            Trick played = trick(
                    play(Seat.NORTH, Rank.QUEEN, Suit.SPADES),
                    play(Seat.WEST, Rank.QUEEN, Suit.DIAMONDS));

            assertEquals(
                    List.of(card(Rank.JACK, Suit.SPADES), card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.SPADES)),
                    LegalMoves.of(hand, played, Seat.SOUTH, Contract.SPADES),
                    "north is south's partner and still the seven may not go under the queen");
        }

        @Test
        @DisplayName("in all trumps every suit is raised")
        void allTrumpsRaisesEverySuit() {
            List<Card> hand = List.of(
                    card(Rank.NINE, Suit.HEARTS),
                    card(Rank.SEVEN, Suit.HEARTS),
                    card(Rank.JACK, Suit.CLUBS));
            Trick played = trick(play(Seat.NORTH, Rank.ACE, Suit.HEARTS));

            assertEquals(List.of(card(Rank.NINE, Suit.HEARTS)),
                    LegalMoves.of(hand, played, Seat.WEST, Contract.ALL_TRUMPS),
                    "hearts are played by the trump order in all trumps, and the nine beats the ace");
        }

        @Test
        @DisplayName("in no trumps you follow, and need not go higher")
        void noTrumpsOnlyFollows() {
            List<Card> hand = List.of(
                    card(Rank.ACE, Suit.HEARTS),
                    card(Rank.SEVEN, Suit.HEARTS),
                    card(Rank.JACK, Suit.CLUBS));
            Trick played = trick(play(Seat.NORTH, Rank.KING, Suit.HEARTS));

            assertEquals(List.of(card(Rank.ACE, Suit.HEARTS), card(Rank.SEVEN, Suit.HEARTS)),
                    LegalMoves.of(hand, played, Seat.WEST, Contract.NO_TRUMPS),
                    "no suit is played by the trump order, so the seven may go under the king");
        }

        @Test
        @DisplayName("and holding nothing higher, any trump of the suit may follow")
        void nothingHigherFollowsFreely() {
            List<Card> hand = List.of(
                    card(Rank.EIGHT, Suit.SPADES),
                    card(Rank.SEVEN, Suit.SPADES),
                    card(Rank.ACE, Suit.HEARTS));
            Trick played = trick(play(Seat.NORTH, Rank.JACK, Suit.SPADES));

            assertEquals(List.of(card(Rank.EIGHT, Suit.SPADES), card(Rank.SEVEN, Suit.SPADES)),
                    LegalMoves.of(hand, played, Seat.WEST, Contract.SPADES),
                    "nothing beats the jack, so you follow with either and keep the ace");
        }

        /**
         * ANSWERED (OPEN 13) — no longer an assumption.
         *
         * <p>"В случай че няма по-висок коз, може да изиграе произволна
         * карта." Kept here beside the raising rule because the two are the
         * same question asked twice, and the answers went different ways.
         */
        @Test
        @DisplayName("holding only trumps too low to win, you may discard instead")
        void undertrumpingIsNotForced() {
            List<Card> hand = List.of(
                    card(Rank.SEVEN, Suit.SPADES),
                    card(Rank.ACE, Suit.CLUBS));
            Trick played = trick(
                    play(Seat.NORTH, Rank.ACE, Suit.DIAMONDS),
                    play(Seat.WEST, Rank.SEVEN, Suit.DIAMONDS),
                    play(Seat.SOUTH, Rank.JACK, Suit.SPADES));

            assertEquals(hand, LegalMoves.of(hand, played, Seat.EAST, Contract.SPADES),
                    "the trick is lost to the jack either way, so the seven is not "
                            + "thrown away on it");
        }
    }

    @Nested
    @DisplayName("seats")
    class Seats {

        @Test
        @DisplayName("play runs counter-clockwise and partners sit opposite")
        void tableGeometry() {
            assertEquals(Seat.WEST, Seat.NORTH.next());
            assertEquals(Seat.NORTH, Seat.EAST.next());
            assertEquals(Seat.SOUTH, Seat.NORTH.partner());
            assertTrue(Seat.NORTH.isOpponentOf(Seat.WEST));
            assertTrue(Seat.NORTH.isPartnerOf(Seat.SOUTH));
        }

        @Test
        @DisplayName("four seats bring the turn back round")
        void fourStepsIsAFullCircle() {
            assertEquals(Seat.NORTH, Seat.NORTH.next().next().next().next());
        }
    }
}
