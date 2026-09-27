package bg.deck.belot.service;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.model.response.BelotTurnView;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotPlayRequest;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.response.BelotBidView;
import bg.deck.belot.model.response.BelotBiddingView;
import bg.deck.belot.model.response.BelotPlayView;
import bg.deck.belot.model.response.BelotPlayedCard;
import bg.deck.belot.model.response.BelotSeatView;
import bg.deck.belot.model.response.BelotStateResponse;
import bg.deck.enums.GameType;
import bg.deck.service.AvailabilityService;
import bg.deck.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

import java.util.List;
import java.util.Optional;

/**
 * What a player asks of belot: sit down, look at the table, say something.
 *
 * <p>Holds no repository of its own. The table is {@link BelotTableService}'s,
 * the deal is {@link BelotDealService}'s, and this arranges them in the order a
 * turn happens: change something, then tell all four what the table looks like
 * from where they are sitting.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotService {

    /** The catalogue code. Not a {@code GameType}: belot keeps out of that enum. */
    public static final String BELOT = "BELOT";

    private final BelotTableService belotTableService;
    private final BelotDealService belotDealService;
    private final BelotPlayService belotPlayService;
    private final BelotTurnService belotTurnService;
    private final BelotPlayerService belotPlayerService;
    private final AvailabilityService availabilityService;
    private final WebSocketService webSocketService;

    /**
     * Sits a player down, and deals if that filled the table.
     *
     * <p>Gated like the other two searches: nobody joins a queue for a game
     * they are not being offered. {@link GameType} is deliberately not involved
     * — belot is not in that enum, by the rule in {@code docs/belot/BUILD.md}.
     */
    @Transactional
    public void search(String username) {
        availabilityService.requireAvailable(BELOT, username);
        belotPlayerService.ensureKnown(username);

        BelotGame table = belotTableService.join(username);
        if (table.isFull() && belotDealService.current(table).isEmpty()) {
            belotDealService.dealNext(table);
        }

        tellEveryone(table);
    }

    /** Sends this player their own view again — a reload, or a reconnect. */
    @Transactional(readOnly = true)
    public void sendState(String username) {
        belotTableService.tableOf(username).ifPresent(table -> tell(table, username));
    }

    /**
     * One turn of the bidding, from whoever is calling.
     *
     * <p>The seat comes from the table, never from the request: a request that
     * could name a seat could name somebody else's.
     */
    @Transactional
    public void bid(String username, BelotBidRequest request) {
        BelotGame table = tableFor(username);
        BelotSeat seat = seatFor(table, username);
        BelotDeal deal = belotDealService.current(table).orElseThrow(
                () -> new IllegalStateException("No deal in progress at table " + table.getId()));

        belotDealService.bid(deal, new BidAction(seat.getSeat(), request.kind(), request.contract()));

        // Nobody wanted it: the next seat deals, and the table is told once.
        if (deal.getStatus() == BelotDealStatus.THROWN_IN) {
            belotDealService.dealNext(table);
        }

        tellEveryone(table);
    }

    /**
     * One card, from whoever is calling.
     *
     * <p>As with a bid, the seat comes from the table rather than the
     * request. When the card was the last of the deal the score sheet is
     * written and, unless that finished the game, the next hand is dealt —
     * so the four of them are told once, about a table that has already
     * moved on.
     */
    @Transactional
    public void play(String username, BelotPlayRequest request) {
        BelotGame table = tableFor(username);
        BelotSeat seat = seatFor(table, username);
        BelotDeal deal = belotDealService.current(table).orElseThrow(
                () -> new IllegalStateException("No deal in progress at table " + table.getId()));

        belotPlayService.play(table, deal, seat.getSeat(), request.card());

        if (deal.getStatus() == BelotDealStatus.FINISHED
                && table.getStatus() != BelotGameStatus.FINISHED) {
            belotDealService.dealNext(table);
        }

        tellEveryone(table);
    }

    private BelotGame tableFor(String username) {
        return belotTableService.tableOf(username).orElseThrow(
                () -> new IllegalStateException(username + " is not at a belot table"));
    }

    private BelotSeat seatFor(BelotGame table, String username) {
        return table.seatOf(username).orElseThrow(
                () -> new IllegalStateException(username + " has no seat at table " + table.getId()));
    }

    /**
     * Acts for every seat that has run out of time, and tells its table.
     *
     * <p>One transaction per table rather than one for the sweep: a table
     * whose deal has moved on since it was read should cost the others
     * nothing, and a failure on one is not a reason to leave the rest
     * waiting.
     *
     * @return how many seats were acted for
     */
    public int actForAbsentPlayers() {
        List<BelotDeal> waiting = belotDealService.waitingSince(
                Instant.now().minus(belotTurnService.turnTimeout()));

        int acted = 0;
        for (BelotDeal deal : waiting) {
            try {
                if (actFor(deal)) {
                    acted++;
                }
            } catch (RuntimeException e) {
                // The table moved on between the sweep reading it and this,
                // or something worse. Either way the other tables are none
                // of its business.
                log.warn("Belot: could not act for the absent seat at deal {}", deal.getId(), e);
            }
        }
        return acted;
    }

    @Transactional
    public boolean actFor(BelotDeal deal) {
        BelotGame table = deal.getGame();
        if (!belotTurnService.actForAbsentPlayer(table, deal)) {
            return false;
        }

        // A thrown-in or finished hand is followed by the next one, exactly
        // as it is when a player does the acting.
        if (deal.getStatus() == BelotDealStatus.THROWN_IN
                || (deal.getStatus() == BelotDealStatus.FINISHED
                    && table.getStatus() != BelotGameStatus.FINISHED)) {
            belotDealService.dealNext(table);
        }

        tellEveryone(table);
        return true;
    }

    private void tellEveryone(BelotGame table) {
        table.getSeats().forEach(seat -> tell(table, seat.getUsername()));
    }

    private void tell(BelotGame table, String username) {
        webSocketService.notifyBelotUpdate(username, viewFor(table, username));
    }

    /** The table as one seat sees it, with that seat's hand and nobody else's. */
    private BelotStateResponse viewFor(BelotGame table, String username) {
        Seat seat = table.seatOf(username).map(BelotSeat::getSeat).orElse(null);
        Optional<BelotDeal> deal = belotDealService.current(table);

        List<Card> hand = List.of();
        if (deal.isPresent() && seat != null) {
            // Five while the bidding is on, then what is left of the eight.
            hand = deal.get().getStatus() == BelotDealStatus.PLAYING
                    ? belotPlayService.handOf(table, deal.get(), seat)
                    : belotDealService.visibleHand(table, deal.get(), seat);
        }

        return new BelotStateResponse(
                table.getId(),
                table.getStatus(),
                table.getServerSeedHash(),
                table.getSeats().stream()
                        .map(taken -> new BelotSeatView(taken.getSeat(), taken.team(),
                                taken.getUsername(), cardsLeft(deal, taken.getSeat())))
                        .toList(),
                seat,
                deal.map(BelotDeal::getDealNumber).orElse(null),
                deal.map(BelotDeal::getDealerSeat).orElse(null),
                deal.map(BelotDeal::getStatus).orElse(null),
                hand,
                deal.map(current -> biddingFor(current, seat)).orElse(null),
                deal.map(current -> playFor(table, current, seat)).orElse(null),
                deal.map(this::turnFor).orElse(null),
                table.getNorthSouthScore(),
                table.getEastWestScore(),
                table.getHangingPoints());
    }

    /**
     * How many cards a seat is still holding — public, as it is at a table.
     *
     * <p>Five during the bidding, because that is all that has been dealt.
     */
    private int cardsLeft(java.util.Optional<BelotDeal> deal, Seat seat) {
        return deal.map(current -> switch (current.getStatus()) {
            case BIDDING -> Dealing.BEFORE_BIDDING;
            case PLAYING -> Dealing.HAND_SIZE - current.playedBy(seat).size();
            case THROWN_IN, FINISHED -> 0;
        }).orElse(0);
    }

    /**
     * The bidding, with the calls this seat may make now and no others.
     *
     * <p>Sending every seat its own legal moves rather than the rules is what
     * keeps the client from having to know them — and from offering a button
     * the server would refuse.
     */
    private BelotBiddingView biddingFor(BelotDeal deal, Seat seat) {
        if (deal.getStatus() != BelotDealStatus.BIDDING) {
            return null;
        }
        Bidding bidding = deal.bidding();

        List<BelotBidView> yours = bidding.toAct() == seat
                ? bidding.legalActions().stream().map(BelotBidView::of).toList()
                : List.of();

        return new BelotBiddingView(
                bidding.toAct(),
                bidding.highestBid(),
                bidding.bidder(),
                bidding.doubling(),
                deal.getBids().stream().map(bid -> BelotBidView.of(bid.action())).toList(),
                yours);
    }

    /**
     * The trick on the table, and what this seat may add to it.
     *
     * <p>{@code yours} is empty unless it is their turn, so the client has
     * no rule to apply and no card to offer that would be refused.
     */
    private BelotPlayView playFor(BelotGame table, BelotDeal deal, Seat seat) {
        if (deal.getStatus() != BelotDealStatus.PLAYING) {
            return null;
        }
        Trick trick = deal.currentTrick();

        return new BelotPlayView(
                deal.getContract(),
                deal.getDeclarerSeat(),
                belotPlayService.toAct(deal),
                deal.currentTrickNumber(),
                trick.plays().stream().map(play -> new BelotPlayedCard(play.seat(), play.card())).toList(),
                seat == null ? List.of() : belotPlayService.legalFor(table, deal, seat));
    }

    /**
     * Whose turn it is and when it runs out.
     *
     * <p>The deadline is sent rather than the seconds left: a client that
     * counts down from a number the server sent drifts, and one that
     * counts to a moment the server sent does not.
     */
    private BelotTurnView turnFor(BelotDeal deal) {
        Optional<Seat> toAct = belotTurnService.toAct(deal);
        if (toAct.isEmpty()) {
            return null;
        }
        return new BelotTurnView(toAct.get(), belotTurnService.deadline(deal).orElse(null));
    }
}
