package bg.deck.belot.model.request;

import bg.deck.belot.enums.BidKind;
import bg.deck.belot.enums.Contract;
import jakarta.validation.constraints.NotNull;

/**
 * What a player says when the bidding reaches them.
 *
 * <p>The seat is not here: it is whoever the token says is calling, looked up
 * at their table. A request that could name a seat could name somebody else's.
 *
 * @param kind     pass, bid, contra or recontra; a body without one is not a turn
 * @param contract the contract named, on a bid and nowhere else
 */
public record BelotBidRequest(@NotNull BidKind kind, Contract contract) {
}
