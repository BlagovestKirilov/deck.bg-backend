package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Doubling;
import bg.deck.belot.enums.Seat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bidding, as a value that never changes: every turn produces a new one.
 *
 * <p>RULES §5. The player to the dealer's right speaks first — which, with play
 * running counter-clockwise, is the seat the deal moves to next. Each bid must
 * beat the last, and three passes in a row end it. Four passes and nobody has
 * bid: the hand is thrown in and the next seat deals.
 *
 * <p>Contra can only come from an opponent of whoever holds the contract, and
 * recontra only from the side that holds it.
 *
 * @param dealer     who dealt this hand
 * @param toAct      whose turn it is to speak
 * @param highestBid the highest contract bid so far, or null
 * @param bidder     the seat that bid it, or null
 * @param doubling   plain, contra'd or recontra'd
 * @param passes     how many have passed in a row
 * @param anyBid     whether anyone has bid at all
 */
public record Bidding(
        Seat dealer,
        Seat toAct,
        Contract highestBid,
        Seat bidder,
        Doubling doubling,
        int passes,
        boolean anyBid
) {

    /** Three in a row, and whoever is left holds the contract. */
    private static final int PASSES_THAT_END_IT = 3;
    /** Nobody wanted it: the hand is thrown in. */
    private static final int PASSES_THAT_THROW_IT_IN = 4;

    public static Bidding startedBy(Seat dealer) {
        return new Bidding(dealer, dealer.next(), null, null, Doubling.NONE, 0, false);
    }

    public boolean isFinished() {
        return isThrownIn() || (anyBid && passes >= PASSES_THAT_END_IT);
    }

    /** True when all four passed without a bid: deal again, RULES §4. */
    public boolean isThrownIn() {
        return !anyBid && passes >= PASSES_THAT_THROW_IT_IN;
    }

    /** The contract as it stands, empty while nobody has bid. */
    public Optional<Contract> contract() {
        return Optional.ofNullable(highestBid);
    }

    /** Whoever bid it. */
    public Optional<Seat> holder() {
        return Optional.ofNullable(bidder);
    }

    /** What the deal is multiplied by: 1, 2 after a contra, 4 after a recontra. */
    public int multiplier() {
        return doubling.multiplier();
    }

    public boolean isLegal(BidAction action) {
        if (isFinished() || action.seat() != toAct) {
            return false;
        }
        return switch (action.kind()) {
            case PASS -> true;
            case BID -> action.contract() != null
                    && (highestBid == null || action.contract().beats(highestBid));
            // Only an opponent of the seat holding the contract, and only once.
            case CONTRA -> bidder != null && doubling == Doubling.NONE && toAct.isOpponentOf(bidder);
            // Only the side that holds it, and only over a contra.
            case RECONTRA -> bidder != null && doubling == Doubling.CONTRA
                    && (toAct == bidder || toAct.isPartnerOf(bidder));
        };
    }

    public List<BidAction> legalActions() {
        if (isFinished()) {
            return List.of();
        }
        List<BidAction> actions = new ArrayList<>();
        actions.add(BidAction.pass(toAct));
        for (Contract candidate : Contract.values()) {
            BidAction bid = BidAction.bid(toAct, candidate);
            if (isLegal(bid)) {
                actions.add(bid);
            }
        }
        if (isLegal(BidAction.contra(toAct))) {
            actions.add(BidAction.contra(toAct));
        }
        if (isLegal(BidAction.recontra(toAct))) {
            actions.add(BidAction.recontra(toAct));
        }
        return List.copyOf(actions);
    }

    /**
     * The bidding after this turn.
     *
     * <p>A bid, a contra and a recontra all break a run of passes: the others
     * have to be given the chance to answer it.
     *
     * <p>OPEN 3 — a higher contract is allowed over a contra here, and it clears
     * the contra with it, because the contra was aimed at the contract that has
     * just been outbid. If the answer is that a contra fixes the contract, this
     * is the method that changes.
     */
    public Bidding apply(BidAction action) {
        if (!isLegal(action)) {
            throw new IllegalArgumentException("Illegal bid: " + action + " on " + this);
        }
        return switch (action.kind()) {
            case PASS -> new Bidding(dealer, toAct.next(), highestBid, bidder, doubling, passes + 1, anyBid);
            case BID -> new Bidding(dealer, toAct.next(), action.contract(), toAct, Doubling.NONE, 0, true);
            case CONTRA -> new Bidding(dealer, toAct.next(), highestBid, bidder, Doubling.CONTRA, 0, true);
            case RECONTRA -> new Bidding(dealer, toAct.next(), highestBid, bidder, Doubling.RECONTRA, 0, true);
        };
    }
}
