package bg.deck.belot.service;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotPlayRequest;
import bg.deck.belot.model.response.BelotBidView;
import bg.deck.belot.model.response.BelotBiddingView;
import bg.deck.belot.model.response.BelotDealRow;
import bg.deck.belot.model.response.BelotDeclarationView;
import bg.deck.belot.model.response.BelotDeclarationsView;
import bg.deck.belot.model.response.BelotPlayView;
import bg.deck.belot.model.response.BelotPlayedCard;
import bg.deck.belot.model.response.BelotSeatView;
import bg.deck.belot.model.response.BelotStateResponse;
import bg.deck.belot.model.response.BelotTurnView;
import bg.deck.enums.GameType;
import bg.deck.exception.IllegalMoveException;
import bg.deck.service.AvailabilityService;
import bg.deck.service.WebSocketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final BelotStatsService belotStatsService;
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
                () -> new IllegalMoveException("No deal in progress at table " + table.getId()));

        try {
            belotDealService.bid(deal, new BidAction(seat.getSeat(), request.kind(), request.contract()));
        } catch (IllegalArgumentException e) {
            // The engine refuses a call that is not legal now. From the
            // player's side that is a call that arrived too late, or a client
            // offering a button it should not have.
            throw new IllegalMoveException(e.getMessage(), e);
        }

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
                () -> new IllegalMoveException("No deal in progress at table " + table.getId()));

        try {
            belotPlayService.play(table, deal, seat.getSeat(), request.card());
        } catch (IllegalArgumentException e) {
            throw new IllegalMoveException(e.getMessage(), e);
        }

        if (deal.getStatus() == BelotDealStatus.FINISHED
                && table.getStatus() != BelotGameStatus.FINISHED) {
            belotDealService.dealNext(table);
        }

        tellEveryone(table);
    }

    /**
     * A player gives up, and the game goes to the other pair.
     *
     * <p>The whole pair gives up with them, which is why the client confirms
     * it in those words.
     *
     * <p>Idempotent on purpose. A table that is already over — because their
     * partner pressed it a moment earlier, or because the last card fell — is
     * left alone rather than refused.
     */
    @Transactional
    public void surrender(String username) {
        Optional<BelotGame> seated = belotTableService.tableOf(username);
        if (seated.isEmpty() || seated.get().getStatus() == BelotGameStatus.FINISHED) {
            // Nothing to give up. Their partner pressed it a moment ago, or
            // the last card has just been played. Neither is an error, and a
            // 500 for the slower of two hands is worse than doing nothing.
            return;
        }

        BelotGame table = seated.get();
        BelotSeat seat = seatFor(table, username);
        belotDealService.current(table).ifPresent(belotDealService::abandon);
        belotTableService.concede(table, seat);
        belotStatsService.record(table);

        tellEveryone(table);
    }

    /**
     * The table this player is at.
     *
     * <p>Not being at one is the player's situation, not a fault: the game
     * finished while their tap was in flight, or they have two tabs open. So
     * it is a refused move rather than a 500.
     */
    private BelotGame tableFor(String username) {
        return belotTableService.tableOf(username).orElseThrow(
                () -> new IllegalMoveException(username + " is not at a belot table"));
    }

    private BelotSeat seatFor(BelotGame table, String username) {
        return table.seatOf(username).orElseThrow(
                () -> new IllegalStateException(username + " has no seat at table " + table.getId()));
    }

    /**
     * The deals somebody has been sitting on for longer than a turn lasts.
     *
     * <p>Read here and acted on one at a time by the sweep, each in a
     * transaction of its own: a table whose deal has moved on since it was
     * read should cost the others nothing, and a failure on one is not a
     * reason to leave the rest waiting.
     *
     * @see bg.deck.belot.scheduler.BelotTurnScheduler for why the loop is not here
     */
    public List<UUID> dealsOutOfTime() {
        return belotDealService.waitingSince(Instant.now().minus(belotTurnService.turnTimeout()))
                .stream()
                .map(BelotDeal::getId)
                .toList();
    }

    /**
     * Acts for the seat at this deal whose time is up, and tells its table.
     *
     * <p>Must be called from another bean. Its transaction is what loads the
     * bids and the plays, and the sweep that used to call it from inside this
     * class got no transaction at all — every table with an absent player
     * threw on the first lazy collection, the sweep logged a warning nobody
     * was reading, and the table sat there for ever. Which is what a player
     * saw: a clock reaching zero and nothing happening.
     *
     * @return whether there was anything to do
     */
    @Transactional
    public boolean actFor(UUID dealId) {
        // Read again, in this transaction. The one the sweep is holding was
        // loaded in another that has since closed, and a detached deal cannot
        // load the bids or the plays that deciding anything needs.
        Optional<BelotDeal> found = belotDealService.byId(dealId);
        if (found.isEmpty()) {
            return false;
        }

        BelotDeal deal = found.get();
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
                table.getWinnerTeam(),
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
                deal.map(current -> declarationsFor(table, current)).orElse(null),
                sheetOf(table),
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
            // A hand that is over, however it ended: nobody is holding
            // anything at the table any more.
            case THROWN_IN, FINISHED, ABANDONED -> 0;
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
        Seat wonBy = null;
        int trickNo = deal.currentTrickNumber();

        // Between the fourth card and the next lead there is no trick in
        // progress, and showing an empty table then means nobody ever sees
        // the trick they just played into, or who took it. So the finished
        // one stays out until somebody leads the next.
        if (trick.isEmpty() && !deal.getPlays().isEmpty()) {
            List<Trick> played = deal.tricks();
            trick = played.getLast();
            wonBy = deal.lastTrickWinner().orElse(null);
            trickNo = played.size();
        }

        return new BelotPlayView(
                deal.getContract(),
                deal.getDeclarerSeat(),
                belotPlayService.toAct(deal),
                trickNo,
                trick.plays().stream().map(play -> new BelotPlayedCard(play.seat(), play.card())).toList(),
                wonBy,
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

    /**
     * The score sheet: every hand that has been counted, oldest first.
     *
     * <p>Sent with the state rather than fetched on demand. A game runs to
     * 151, which is a dozen or so lines — small enough that a second
     * request to keep them in step with the running total would cost more
     * than it saves.
     */
    private List<BelotDealRow> sheetOf(BelotGame table) {
        return belotDealService.history(table).stream()
                .filter(played -> played.getResult() != null)
                .map(played -> rowFor(table, played))
                .toList();
    }

    /**
     * One counted hand, as a line of the sheet.
     *
     * <p>The announcements are worked out again from the hands rather than
     * stored: they are a function of what was dealt and what was called, and
     * both of those are on the deal. A game is a dozen hands, so the cost of
     * re-deriving them is a dozen shuffles of a known seed.
     */
    private BelotDealRow rowFor(BelotGame table, BelotDeal played) {
        Team caller = played.getDeclarerSeat() == null ? null : Team.of(played.getDeclarerSeat());

        int callerDeclarations = 0;
        int opponentDeclarations = 0;
        if (caller != null) {
            Map<Seat, List<Declaration>> bySeat = belotPlayService.declarationsBySeat(table, played);
            callerDeclarations = belotPlayService.declarationPoints(bySeat, caller);
            opponentDeclarations = belotPlayService.declarationPoints(bySeat, caller.opponent());
        }

        return new BelotDealRow(
                played.getDealNumber(),
                played.getContract(),
                played.getDeclarerSeat(),
                caller,
                played.getDoubling(),
                played.getCallerPoints(),
                played.getOpponentPoints(),
                callerDeclarations,
                opponentDeclarations,
                played.getCallerScore(),
                played.getOpponentScore(),
                played.getResult());
    }

    /**
     * What the table announced, once the first trick is complete.
     *
     * <p>Not before: at a table these are called out as the first trick is
     * played, and sending them sooner would tell three people what is in
     * somebody’s hand before they have played a card of it.
     */
    private BelotDeclarationsView declarationsFor(BelotGame table, BelotDeal deal) {
        boolean firstTrickDone = deal.getPlays().size() >= Seat.values().length;
        if (deal.getContract() == null || !firstTrickDone) {
            return null;
        }

        Map<Seat, List<Declaration>> bySeat = belotPlayService.declarationsBySeat(table, deal);
        List<BelotDeclarationView> shown = bySeat.entrySet().stream()
                .flatMap(held -> held.getValue().stream()
                        .map(declaration -> BelotDeclarationView.of(held.getKey(), declaration)))
                .toList();

        return new BelotDeclarationsView(shown,
                belotPlayService.declarationPoints(bySeat, Team.NORTH_SOUTH),
                belotPlayService.declarationPoints(bySeat, Team.EAST_WEST));
    }
}
