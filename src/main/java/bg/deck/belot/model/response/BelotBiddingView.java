package bg.deck.belot.model.response;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Doubling;
import bg.deck.belot.enums.Seat;

import java.util.List;

/**
 * The bidding as one seat sees it.
 *
 * <p>Everything here is public at the table — what was said is said out loud —
 * except {@code yours}, which is the list of calls this particular player may
 * make right now. It is empty when it is not their turn, so the client has
 * nothing to decide and cannot offer a button the server would refuse.
 *
 * @param toAct      whose turn it is to speak
 * @param highestBid the contract as it stands, or null while nobody has bid
 * @param bidder     who bid it, or null
 * @param doubling   plain, contra'd or recontra'd
 * @param said       every turn so far, in order
 * @param yours      what the player being sent this may say now
 */
public record BelotBiddingView(
        Seat toAct,
        Contract highestBid,
        Seat bidder,
        Doubling doubling,
        List<BelotBidView> said,
        List<BelotBidView> yours
) {

    public BelotBiddingView {
        said = List.copyOf(said);
        yours = List.copyOf(yours);
    }
}
