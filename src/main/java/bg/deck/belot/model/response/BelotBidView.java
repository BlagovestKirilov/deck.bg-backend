package bg.deck.belot.model.response;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.enums.BidKind;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Seat;

/**
 * One turn of the bidding, for the client: what was said, and by whom.
 *
 * @param seat     who spoke
 * @param kind     what they said
 * @param contract the contract named, on a bid and nowhere else
 */
public record BelotBidView(Seat seat, BidKind kind, Contract contract) {

    public static BelotBidView of(BidAction action) {
        return new BelotBidView(action.seat(), action.kind(), action.contract());
    }
}
