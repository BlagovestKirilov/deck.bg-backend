package bg.deck.belot.engine;

import bg.deck.belot.enums.BidKind;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Seat;

/**
 * One turn of the bidding.
 *
 * @param seat     who is speaking
 * @param kind     what they said
 * @param contract the contract named, on a {@link BidKind#BID} and nowhere else
 */
public record BidAction(Seat seat, BidKind kind, Contract contract) {

    public static BidAction pass(Seat seat) {
        return new BidAction(seat, BidKind.PASS, null);
    }

    public static BidAction bid(Seat seat, Contract contract) {
        return new BidAction(seat, BidKind.BID, contract);
    }

    public static BidAction contra(Seat seat) {
        return new BidAction(seat, BidKind.CONTRA, null);
    }

    public static BidAction recontra(Seat seat) {
        return new BidAction(seat, BidKind.RECONTRA, null);
    }
}
