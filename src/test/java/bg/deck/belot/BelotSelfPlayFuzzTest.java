package bg.deck.belot;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.CardPoints;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.engine.DealOutcome;
import bg.deck.belot.engine.DealPlay;
import bg.deck.belot.engine.DealPoints;
import bg.deck.belot.engine.DealRounding;
import bg.deck.belot.engine.DealScorer;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.Declarations;
import bg.deck.belot.engine.PlayedDeal;
import bg.deck.belot.engine.RecordedScore;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.engine.ShuffleStream;
import bg.deck.belot.enums.Team;
import bg.deck.belot.engine.Trick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thousands of deals played out at random, checked against the things that must
 * be true of every one of them.
 *
 * <p>This is the checkpoint before any of it is put on a socket. A rule read
 * wrongly shows up here as arithmetic that does not add up, which is the same
 * way a player would find it — except that here it is found before they do, and
 * the seed that produced it is printed.
 *
 * <p>Modelled on the Tabla engine's own fuzz test.
 */
@DisplayName("Self-play")
class BelotSelfPlayFuzzTest {

    private static final int DEALS = 2_000;

    @Test
    @DisplayName("every deal adds up, in every contract")
    void everyDealAddsUp() {
        Random random = new Random(20260924L);

        for (int deal = 0; deal < DEALS; deal++) {
            long seed = random.nextLong();
            try {
                playOneDeal(seed);
            } catch (AssertionError | RuntimeException failure) {
                throw new AssertionError("deal with seed " + seed + " failed: " + failure.getMessage(), failure);
            }
        }
    }

    /**
     * A keystream for the fuzz run.
     *
     * <p>The shuffle takes bytes now rather than a {@link Random}, and the
     * point of this test is the rules, not the shuffle: any bytes will do, so
     * long as the seed printed on a failure reproduces them.
     */
    private static ShuffleStream streamFrom(Random random) {
        byte[] bytes = new byte[256];
        random.nextBytes(bytes);
        return new ShuffleStream(bytes);
    }

    private static void playOneDeal(long seed) {
        Random random = new Random(seed);
        Seat dealer = Seat.values()[random.nextInt(Seat.values().length)];

        Map<Seat, List<Card>> hands = Dealing.deal(Dealing.shuffled(streamFrom(random)), dealer);
        assertDealtProperly(hands);

        Bidding bidding = bidRandomly(Bidding.startedBy(dealer), random);
        if (bidding.isThrownIn()) {
            return;
        }

        Contract contract = bidding.contract().orElseThrow();
        Seat holder = bidding.holder().orElseThrow();

        // Declared from the full hand, before a card is played.
        List<Declaration> northSouth = declarationsOf(hands, Team.NORTH_SOUTH, contract);
        List<Declaration> eastWest = declarationsOf(hands, Team.EAST_WEST, contract);

        PlayedDeal played = DealPlay.play(hands, contract, dealer.next(), (seat, legal, trick) ->
                legal.get(random.nextInt(legal.size())));

        assertEveryCardPlayedOnce(played);

        // The cards and the last trick are exactly what the contract is worth.
        Map<Team, Integer> fromTricks = DealPoints.fromTricks(played, contract);
        assertEquals(CardPoints.dealTotal(contract),
                fromTricks.get(Team.NORTH_SOUTH) + fromTricks.get(Team.EAST_WEST),
                "the cards and the last ten, in " + contract);

        // And the sheet adds up, whatever was declared on top.
        Map<Team, Integer> total = DealPoints.total(played, contract, northSouth, eastWest);
        Team callers = Team.of(holder);
        int callerPoints = total.get(callers);
        int opponentPoints = total.get(callers.opponent());

        DealOutcome outcome = DealScorer.score(callerPoints, opponentPoints, bidding.multiplier(), 0);
        assertNothingIsLost(outcome, callerPoints, opponentPoints, bidding.multiplier());
    }

    /** Four hands of eight, every card once, none conjured. */
    private static void assertDealtProperly(Map<Seat, List<Card>> hands) {
        List<Card> all = hands.values().stream().flatMap(List::stream).toList();

        assertEquals(32, all.size(), "the whole deck is dealt");
        assertEquals(32, Set.copyOf(all).size(), "and no card twice");
        hands.forEach((seat, hand) ->
                assertEquals(Dealing.HAND_SIZE, hand.size(), seat + " holds eight"));
    }

    private static void assertEveryCardPlayedOnce(PlayedDeal played) {
        List<Card> played32 = played.tricks().stream()
                .flatMap(trick -> trick.plays().stream())
                .map(play -> play.card())
                .toList();

        assertEquals(32, played32.size(), "eight tricks of four");
        assertEquals(32, new HashSet<>(played32).size(), "no card played twice");
        played.tricks().forEach(trick ->
                assertEquals(4, trick.plays().size(), "every trick is complete"));
    }

    /**
     * The deal is accounted for: what goes on the sheet, plus anything left
     * hanging for the next one, is the deal itself times the doubling.
     *
     * <p>Висящи is why the hanging points belong in this sum — the fuzz's first
     * run found a level deal recording 8 of its 16 and called it a loss, when
     * the other 8 were sitting on the table waiting, exactly as they should.
     */
    private static void assertNothingIsLost(DealOutcome outcome, int callerPoints, int opponentPoints, int multiplier) {
        int expected = DealRounding.nearest(callerPoints + opponentPoints) * multiplier;
        RecordedScore recorded = outcome.recorded();

        assertEquals(expected, recorded.total() + outcome.hanging(),
                outcome.result() + ": recorded " + recorded + " with " + outcome.hanging()
                        + " hanging, for " + callerPoints + " against " + opponentPoints);
    }

    private static List<Declaration> declarationsOf(Map<Seat, List<Card>> hands, Team team, Contract contract) {
        return Stream.of(Seat.values())
                .filter(seat -> Team.of(seat) == team)
                .flatMap(seat -> Declarations.in(hands.get(seat), contract).stream())
                .toList();
    }

    /** Bids at random from whatever is legal, until the bidding is over. */
    private static Bidding bidRandomly(Bidding bidding, Random random) {
        Bidding state = bidding;
        int guard = 0;
        while (!state.isFinished()) {
            List<BidAction> legal = new ArrayList<>(state.legalActions());
            // Weighted towards passing, or the bidding wanders on for ever.
            BidAction action = random.nextInt(3) == 0
                    ? legal.get(random.nextInt(legal.size()))
                    : legal.getFirst();
            state = state.apply(action);

            if (++guard > 100) {
                throw new IllegalStateException("bidding did not settle");
            }
        }
        return state;
    }

    @Test
    @DisplayName("a trick is always taken by a card that was in it")
    void trickWinnersAreRealPlayers() {
        Random random = new Random(7L);
        Seat dealer = Seat.NORTH;
        Map<Seat, List<Card>> hands = Dealing.deal(Dealing.shuffled(streamFrom(random)), dealer);

        PlayedDeal played = DealPlay.play(hands, Contract.SPADES, dealer.next(),
                (seat, legal, trick) -> legal.getFirst());

        for (int i = 0; i < played.tricks().size(); i++) {
            Trick trick = played.tricks().get(i);
            Seat winner = played.winners().get(i);

            assertTrue(trick.plays().stream().anyMatch(play -> play.seat() == winner),
                    "the winner played in the trick");
        }
    }
}
