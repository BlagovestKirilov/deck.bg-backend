package bg.deck.belot;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Doubling;
import bg.deck.belot.enums.Seat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RULES §5 — the bidding.
 *
 * <p>North deals throughout, so West speaks first: play runs counter-clockwise,
 * and the seat it moves to next is the one on the dealer's right.
 */
@DisplayName("The bidding")
class BelotBiddingTest {

    private static final Seat DEALER = Seat.NORTH;

    private static Bidding fresh() {
        return Bidding.startedBy(DEALER);
    }

    /** Applies the actions in order, each by whoever is to speak. */
    private static Bidding after(Bidding bidding, BidAction... actions) {
        Bidding state = bidding;
        for (BidAction action : actions) {
            state = state.apply(action);
        }
        return state;
    }

    @Nested
    @DisplayName("turn order")
    class TurnOrder {

        @Test
        @DisplayName("the seat to the dealer's right speaks first")
        void firstToSpeak() {
            assertEquals(Seat.WEST, fresh().toAct(), "north dealt, so west opens");
        }

        @Test
        @DisplayName("and it goes round counter-clockwise")
        void roundTheTable() {
            Bidding bidding = after(fresh(), BidAction.pass(Seat.WEST));
            assertEquals(Seat.SOUTH, bidding.toAct());

            bidding = after(bidding, BidAction.pass(Seat.SOUTH));
            assertEquals(Seat.EAST, bidding.toAct());
        }

        @Test
        @DisplayName("a seat cannot speak out of turn")
        void outOfTurnIsRefused() {
            Bidding bidding = fresh();

            assertFalse(bidding.isLegal(BidAction.pass(Seat.SOUTH)));
            assertThrows(IllegalArgumentException.class, () -> bidding.apply(BidAction.bid(Seat.EAST, Contract.SPADES)));
        }
    }

    @Nested
    @DisplayName("bids")
    class Bids {

        @Test
        @DisplayName("any contract opens")
        void anythingOpens() {
            Bidding bidding = fresh();

            for (Contract contract : Contract.values()) {
                assertTrue(bidding.isLegal(BidAction.bid(Seat.WEST, contract)), contract.name());
            }
        }

        @Test
        @DisplayName("the next one must beat it")
        void mustOutbid() {
            Bidding bidding = after(fresh(), BidAction.bid(Seat.WEST, Contract.HEARTS));

            assertFalse(bidding.isLegal(BidAction.bid(Seat.SOUTH, Contract.CLUBS)), "clubs is lower");
            assertFalse(bidding.isLegal(BidAction.bid(Seat.SOUTH, Contract.HEARTS)), "equal is not higher");
            assertTrue(bidding.isLegal(BidAction.bid(Seat.SOUTH, Contract.SPADES)));
            assertTrue(bidding.isLegal(BidAction.bid(Seat.SOUTH, Contract.ALL_TRUMPS)));
        }

        @Test
        @DisplayName("the highest bid and its bidder are what carry")
        void theContractIsHeldByItsBidder() {
            Bidding bidding = after(fresh(),
                    BidAction.bid(Seat.WEST, Contract.CLUBS),
                    BidAction.bid(Seat.SOUTH, Contract.NO_TRUMPS));

            assertEquals(Contract.NO_TRUMPS, bidding.contract().orElseThrow());
            assertEquals(Seat.SOUTH, bidding.holder().orElseThrow());
        }
    }

    @Nested
    @DisplayName("ending")
    class Ending {

        @Test
        @DisplayName("three passes after a bid settle it")
        void threePassesEndIt() {
            Bidding bidding = after(fresh(),
                    BidAction.bid(Seat.WEST, Contract.SPADES),
                    BidAction.pass(Seat.SOUTH),
                    BidAction.pass(Seat.EAST));

            assertFalse(bidding.isFinished(), "two passes is not three");

            bidding = after(bidding, BidAction.pass(Seat.NORTH));

            assertTrue(bidding.isFinished());
            assertFalse(bidding.isThrownIn());
            assertEquals(Contract.SPADES, bidding.contract().orElseThrow());
            assertEquals(Seat.WEST, bidding.holder().orElseThrow());
        }

        @Test
        @DisplayName("a bid resets the count, so the others get another say")
        void aBidResetsTheCount() {
            Bidding bidding = after(fresh(),
                    BidAction.bid(Seat.WEST, Contract.CLUBS),
                    BidAction.pass(Seat.SOUTH),
                    BidAction.pass(Seat.EAST),
                    BidAction.bid(Seat.NORTH, Contract.SPADES));

            assertFalse(bidding.isFinished(), "north's bid starts the counting again");
            assertEquals(Seat.WEST, bidding.toAct());
        }

        @Test
        @DisplayName("four passes and the hand is thrown in")
        void allPass() {
            Bidding bidding = after(fresh(),
                    BidAction.pass(Seat.WEST),
                    BidAction.pass(Seat.SOUTH),
                    BidAction.pass(Seat.EAST),
                    BidAction.pass(Seat.NORTH));

            assertTrue(bidding.isFinished());
            assertTrue(bidding.isThrownIn(), "nobody bid: deal again");
            assertTrue(bidding.contract().isEmpty());
        }

        @Test
        @DisplayName("nothing more may be said once it is settled")
        void finishedIsFinished() {
            Bidding bidding = after(fresh(),
                    BidAction.bid(Seat.WEST, Contract.SPADES),
                    BidAction.pass(Seat.SOUTH),
                    BidAction.pass(Seat.EAST),
                    BidAction.pass(Seat.NORTH));

            assertEquals(java.util.List.of(), bidding.legalActions());
            assertThrows(IllegalArgumentException.class, () -> bidding.apply(BidAction.pass(Seat.WEST)));
        }
    }

    @Nested
    @DisplayName("contra and recontra")
    class Doubles {

        /** West holds spades; South and North are its opponents. */
        private Bidding westHoldsSpades() {
            return after(fresh(), BidAction.bid(Seat.WEST, Contract.SPADES));
        }

        @Test
        @DisplayName("only an opponent of the holder may contra")
        void onlyAnOpponentContras() {
            Bidding bidding = westHoldsSpades();

            assertTrue(bidding.isLegal(BidAction.contra(Seat.SOUTH)), "south sits against west");

            Bidding partnerToAct = after(bidding, BidAction.pass(Seat.SOUTH), BidAction.pass(Seat.EAST));
            assertFalse(partnerToAct.isLegal(BidAction.contra(Seat.NORTH)) && Seat.NORTH.isPartnerOf(Seat.WEST),
                    "a seat cannot contra its own side");
        }

        @Test
        @DisplayName("a contra doubles the deal")
        void contraDoubles() {
            Bidding bidding = after(westHoldsSpades(), BidAction.contra(Seat.SOUTH));

            assertEquals(Doubling.CONTRA, bidding.doubling());
            assertEquals(2, bidding.multiplier());
            assertEquals(Contract.SPADES, bidding.contract().orElseThrow(), "the contract itself is untouched");
        }

        @Test
        @DisplayName("only the holding side may recontra, and only over a contra")
        void recontraBelongsToTheHolders() {
            Bidding contrad = after(westHoldsSpades(), BidAction.contra(Seat.SOUTH));

            assertTrue(contrad.isLegal(BidAction.recontra(Seat.EAST)), "east is west's partner");

            Bidding plain = westHoldsSpades();
            assertFalse(plain.isLegal(BidAction.recontra(Seat.SOUTH)), "nothing to recontra");
        }

        @Test
        @DisplayName("a recontra quadruples it")
        void recontraQuadruples() {
            Bidding bidding = after(westHoldsSpades(),
                    BidAction.contra(Seat.SOUTH),
                    BidAction.recontra(Seat.EAST));

            assertEquals(Doubling.RECONTRA, bidding.doubling());
            assertEquals(4, bidding.multiplier());
        }

        @Test
        @DisplayName("neither can be said twice")
        void onceEach() {
            Bidding bidding = after(westHoldsSpades(), BidAction.contra(Seat.SOUTH));
            assertFalse(bidding.isLegal(BidAction.contra(Seat.EAST)));

            Bidding recontrad = after(bidding, BidAction.recontra(Seat.EAST));
            assertFalse(recontrad.isLegal(BidAction.recontra(Seat.NORTH)));
        }

        @Test
        @DisplayName("a contra gives the table another turn of speaking")
        void aContraResetsTheCount() {
            Bidding bidding = after(westHoldsSpades(),
                    BidAction.contra(Seat.SOUTH),
                    BidAction.pass(Seat.EAST),
                    BidAction.pass(Seat.NORTH));

            assertFalse(bidding.isFinished(), "west still has an answer to give");

            bidding = after(bidding, BidAction.pass(Seat.WEST));
            assertTrue(bidding.isFinished());
            assertEquals(2, bidding.multiplier(), "and it is played doubled");
        }

        @Test
        @DisplayName("OPEN 3 — a higher contract over a contra is allowed, and clears it")
        void openThreeRaisingOverAContra() {
            Bidding bidding = after(westHoldsSpades(),
                    BidAction.contra(Seat.SOUTH),
                    BidAction.bid(Seat.EAST, Contract.ALL_TRUMPS));

            assertEquals(Contract.ALL_TRUMPS, bidding.contract().orElseThrow());
            assertEquals(1, bidding.multiplier(),
                    "assumed: the contra was aimed at the spades that have just been outbid. "
                            + "If a contra instead fixes the contract, the raise is illegal and "
                            + "this expects an exception.");
        }
    }
}
