package bg.deck.belot.model.response;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGameStatus;

import java.util.List;
import java.util.UUID;

/**
 * The table as one player sees it — and nothing they may not see.
 *
 * <p>A separate one of these is built for every seat. {@code yourHand} is the
 * only private thing in it, and it is the reason the view is per seat rather
 * than one state broadcast to the table: a hand that reaches the wrong client
 * has been leaked no matter what the client then does with it.
 *
 * @param gameId          the table
 * @param status          waiting for players, playing, or over
 * @param serverSeedHash  committed before the first card; the seed follows at the end
 * @param seats           who is sitting where
 * @param yourSeat        where the player being sent this is sitting
 * @param dealNumber      which hand, counted from one; null before the first
 * @param dealerSeat      who dealt it
 * @param dealStatus      bidding, playing, thrown in or finished
 * @param yourHand        this player's cards: five during the bidding, eight after
 * @param bidding         the bidding, or null once a hand is being played
 * @param play            the hand being played, or null while it is being bid for
 * @param turn            who the table is waiting for, and until when
 * @param northSouthScore the score sheet
 * @param eastWestScore   the score sheet
 * @param hangingPoints   points from a level deal, waiting on the next one
 */
public record BelotStateResponse(
        UUID gameId,
        BelotGameStatus status,
        String serverSeedHash,
        List<BelotSeatView> seats,
        Seat yourSeat,
        Integer dealNumber,
        Seat dealerSeat,
        BelotDealStatus dealStatus,
        List<Card> yourHand,
        BelotBiddingView bidding,
        BelotPlayView play,
        BelotTurnView turn,
        int northSouthScore,
        int eastWestScore,
        int hangingPoints
) {

    public BelotStateResponse {
        seats = List.copyOf(seats);
        yourHand = List.copyOf(yourHand);
    }
}
