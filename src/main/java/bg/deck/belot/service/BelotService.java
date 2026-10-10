package bg.deck.belot.service;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.DeclarationKind;
import bg.deck.belot.engine.Rank;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotForfeit;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.BelotPlay;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.model.event.BelotTurnClock;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotCutRequest;
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
import bg.deck.belot.model.response.BelotTrickView;
import bg.deck.belot.model.response.BelotTurnView;
import bg.deck.common.enums.GameType;
import bg.deck.common.exception.IllegalMoveException;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.service.WebSocketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * How many times a player's time may run out in one game. At the third
     * their pair gives the game up, as at the santase and tabla tables — and
     * a table nobody is playing at any more ends, rather than playing itself
     * for ever.
     */
    public static final int MISSED_TURNS_TO_FORFEIT = 3;

    private final BelotTableService belotTableService;
    private final BelotDealService belotDealService;
    private final BelotPlayService belotPlayService;
    private final BelotTurnService belotTurnService;
    private final BelotPlayerService belotPlayerService;
    private final BelotStatsService belotStatsService;
    private final AvailabilityService availabilityService;
    private final WebSocketService webSocketService;
    private final ApplicationEventPublisher eventPublisher;

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
            dealNext(table, Duration.ZERO);
        }

        tellEveryone(table);
    }

    /**
     * Sends this player their own view again — a reload, or a reconnect.
     *
     * @return whether they are at a table at all; a player at none has nothing
     *         to be sent, and the screen offers them the way to find one
     */
    @Transactional(readOnly = true)
    public boolean sendState(String username) {
        Optional<BelotGame> table = belotTableService.tableOf(username);
        table.ifPresent(seated -> tell(seated, username));
        return table.isPresent();
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
        if (deal.isAwaitingCut()) {
            throw new IllegalMoveException("The deck has not been cut yet at table " + table.getId());
        }

        try {
            belotDealService.bid(deal, new BidAction(seat.getSeat(), request.kind(), request.contract()));
        } catch (IllegalArgumentException e) {
            // The engine refuses a call that is not legal now. From the
            // player's side that is a call that arrived too late, or a client
            // offering a button it should not have.
            throw new IllegalMoveException(e.getMessage(), e);
        }
        belotDealService.passWhereThereIsNoChoice(deal);

        // Nobody wanted it: the next seat deals, and the table is told once.
        if (deal.getStatus() == BelotDealStatus.THROWN_IN) {
            dealNext(table, Duration.ZERO);
        }

        tellEveryone(table);
    }

    /**
     * The player on the dealer's left cuts the deck.
     *
     * <p>Only the picture of a cut — the hand was dealt from the seed before
     * it, so where the deck is cut changes no card. It is kept on the deal so
     * that every screen shows the same cut at the same moment, and the bidding
     * waits for it.
     */
    @Transactional
    public void cut(String username, BelotCutRequest request) {
        BelotGame table = tableFor(username);
        BelotSeat seat = seatFor(table, username);
        BelotDeal deal = belotDealService.current(table).orElseThrow(
                () -> new IllegalMoveException("No deal in progress at table " + table.getId()));
        if (!deal.isAwaitingCut()) {
            throw new IllegalMoveException("Nothing to cut at table " + table.getId());
        }
        if (deal.cutter() != seat.getSeat()) {
            throw new IllegalMoveException(seat.getSeat() + " does not cut at table " + table.getId());
        }

        belotDealService.cut(deal, request.at(), Instant.now().plus(belotTurnService.dealPause()));
        tellEveryone(table);
    }

    /**
     * Deals the next hand and holds it for the cut, whose clock starts once
     * {@code pause} is over — the count of the hand before, if there was one.
     */
    private void dealNext(BelotGame table, Duration pause) {
        BelotDeal next = belotDealService.dealNext(table);
        belotDealService.awaitCut(next, Instant.now().plus(pause));
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
        if (belotTurnService.isTakingTrick(deal, Instant.now())) {
            throw new IllegalMoveException("The last trick is still being taken at table " + table.getId());
        }

        try {
            belotPlayService.play(table, deal, seat.getSeat(), request.card());
        } catch (IllegalArgumentException e) {
            throw new IllegalMoveException(e.getMessage(), e);
        }

        if (deal.getStatus() == BelotDealStatus.FINISHED
                && table.getStatus() != BelotGameStatus.FINISHED) {
            dealNext(table, belotTurnService.handPause());
        }

        tellEveryone(table);
    }

    /**
     * Gets up from a table still short of four — back to the games, or the
     * page closed. The others at it are told a seat is free again. A table
     * already being played is not left this way: that is {@link #surrender}.
     */
    @Transactional
    public void leave(String username) {
        belotTableService.leaveWaiting(username).ifPresent(this::tellEveryone);
    }

    /**
     * A player gives up, and the game goes to the other pair.
     *
     * <p>It costs whoever gave up twice the rating and gives their partner the
     * win ({@link BelotStatsService#record}), which is what the client says
     * before it asks.
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
        forfeit(table, seat, BelotForfeit.SURRENDER);
    }

    /**
     * The game goes to the other pair: the hand in progress is given up on,
     * the result is written for all four, and the table is told.
     */
    private void forfeit(BelotGame table, BelotSeat seat, BelotForfeit how) {
        belotDealService.current(table)
                .filter(deal -> deal.getStatus() == BelotDealStatus.BIDDING
                        || deal.getStatus() == BelotDealStatus.PLAYING)
                .ifPresent(belotDealService::abandon);
        belotTableService.concede(table, seat, how);
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

    private BelotSeat seatAt(BelotGame table, Seat seat) {
        return table.getSeats().stream()
                .filter(taken -> taken.getSeat() == seat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + seat + " at table " + table.getId()));
    }

    private BelotSeat seatFor(BelotGame table, String username) {
        return table.seatOf(username).orElseThrow(
                () -> new IllegalStateException(username + " has no seat at table " + table.getId()));
    }

    /** The clock of every table with a turn on, for setting the timers again after a restart. */
    @Transactional(readOnly = true)
    public List<BelotTurnClock> liveClocks() {
        return belotDealService.inProgress().stream()
                .map(this::clockFor)
                .filter(BelotTurnClock::isRunning)
                .toList();
    }

    /** Where this deal's clock stands now, read fresh — empty when nobody is to act. */
    @Transactional(readOnly = true)
    public Optional<BelotTurnClock> clockOf(UUID dealId) {
        return belotDealService.byId(dealId)
                .map(this::clockFor)
                .filter(BelotTurnClock::isRunning);
    }

    /**
     * Acts for the seat at this deal whose time is up, and tells its table.
     *
     * <p>Must be called from another bean — {@link BelotTurnTimer} does. Its
     * transaction is what loads the bids and the plays, and a caller inside
     * this class gets no transaction at all: every table with an absent
     * player threw on the first lazy collection, and the table sat there for
     * ever. Which is what a player saw: a clock reaching zero and nothing
     * happening.
     *
     * @return whether there was anything to do
     */
    @Transactional
    public boolean actFor(UUID dealId) {
        // Read in this transaction: a detached deal cannot load the bids or
        // the plays that deciding anything needs.
        return belotDealService.byId(dealId).map(this::actOn).orElse(false);
    }

    /**
     * A screen at this player's table says the clock has reached nought.
     *
     * <p>{@link BelotTurnTimer} gets there too, a moment after the deadline.
     * The screens are kept as well: whichever arrives first is acted on,
     * so a table never waits on one of them alone. The rest find the
     * turn already taken and do nothing — and should two arrive together,
     * the second is refused by the unique place every bid and card has.
     *
     * <p>Nothing is taken on the screen's word: the deadline is the server's,
     * and a turn that has not run out is left alone.
     */
    @Transactional
    public void timeUp(String username) {
        belotTableService.tableOf(username)
                .flatMap(belotDealService::current)
                .ifPresent(this::actOn);
    }

    private boolean actOn(BelotDeal deal) {
        BelotGame table = deal.getGame();
        // Read before acting: acting moves the turn on.
        Optional<Seat> absent = belotTurnService.toAct(deal);
        boolean cutting = deal.isAwaitingCut();
        if (!belotTurnService.actForAbsentPlayer(table, deal)) {
            return false;
        }

        // A missed cut is not counted. The deck is cut for them and no card
        // changes, so it is not a turn the table lost; a bid or a card is.
        // Nor is a turn that ended the game: there is nothing left to give up.
        if (absent.isPresent() && !cutting && table.getStatus() != BelotGameStatus.FINISHED
                && belotTableService.missedTurn(table, absent.get()) >= MISSED_TURNS_TO_FORFEIT) {
            forfeit(table, seatAt(table, absent.get()), BelotForfeit.INACTIVITY);
            return true;
        }

        belotDealService.passWhereThereIsNoChoice(deal);

        // A thrown-in or finished hand is followed by the next one, exactly
        // as it is when a player does the acting.
        if (deal.getStatus() == BelotDealStatus.THROWN_IN) {
            dealNext(table, Duration.ZERO);
        } else if (deal.getStatus() == BelotDealStatus.FINISHED
                && table.getStatus() != BelotGameStatus.FINISHED) {
            dealNext(table, belotTurnService.handPause());
        }

        tellEveryone(table);
        return true;
    }

    /**
     * Tells all four what happened, and sets the turn clock to match.
     *
     * <p>Every change to a table ends here, so this is the one place the
     * clock is published from: whatever moved the table, its timer follows.
     */
    private void tellEveryone(BelotGame table) {
        table.getSeats().forEach(seat -> tell(table, seat.getUsername()));
        eventPublisher.publishEvent(belotDealService.current(table)
                .map(this::clockFor)
                .orElseGet(() -> new BelotTurnClock(table.getId(), null, null)));
    }

    private BelotTurnClock clockFor(BelotDeal deal) {
        return new BelotTurnClock(deal.getGame().getId(), deal.getId(),
                belotTurnService.deadline(deal).orElse(null));
    }

    private void tell(BelotGame table, String username) {
        webSocketService.notifyBelotUpdate(username, viewFor(table, username));
    }

    /** The table as one seat sees it, with that seat's hand and nobody else's. */
    private BelotStateResponse viewFor(BelotGame table, String username) {
        Seat seat = table.seatOf(username).map(BelotSeat::getSeat).orElse(null);
        Optional<BelotDeal> deal = belotDealService.current(table);
        List<BelotDeal> history = belotDealService.history(table);

        List<Card> hand = List.of();
        if (deal.isPresent() && seat != null) {
            // Five while the bidding is on, then what is left of the eight —
            // and nothing once the hand is over. A finished deal still has
            // the eight it was dealt, and sending those back put a full hand
            // in front of a player whose game had just ended.
            hand = switch (deal.get().getStatus()) {
                case BIDDING -> belotDealService.visibleHand(table, deal.get(), seat);
                case PLAYING -> belotPlayService.handOf(table, deal.get(), seat);
                case THROWN_IN, FINISHED, ABANDONED -> List.of();
            };
        }

        return new BelotStateResponse(
                table.getId(),
                table.getStatus(),
                table.getWinnerTeam(),
                table.getForfeit(),
                table.getForfeitedBy(),
                table.getServerSeedHash(),
                table.getSeats().stream()
                        .map(taken -> new BelotSeatView(taken.getSeat(), taken.team(),
                                taken.getUsername(), cardsLeft(deal, taken.getSeat()), taken.getMissedTurns()))
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
                sheetOf(table, history),
                lastTrickOf(history),
                deal.isPresent() ? deal.get().getCutAt() : Integer.valueOf(0),
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
        return new BelotTurnView(toAct.get(), deal.getTurnStartedAt(), belotTurnService.deadline(deal).orElse(null));
    }

    /**
     * The score sheet: every hand that has been counted, oldest first.
     *
     * <p>Sent with the state rather than fetched on demand. A game runs to
     * 151, which is a dozen or so lines — small enough that a second
     * request to keep them in step with the running total would cost more
     * than it saves.
     */
    private List<BelotDealRow> sheetOf(BelotGame table, List<BelotDeal> history) {
        return history.stream()
                .filter(played -> played.getResult() != null)
                .map(played -> rowFor(table, played))
                .toList();
    }

    /**
     * The last trick of the newest counted hand, if it was played to the end.
     *
     * <p>A thrown-in hand is counted too, but nothing was played in it, so
     * there is no trick to show.
     */
    private BelotTrickView lastTrickOf(List<BelotDeal> history) {
        return history.stream()
                .filter(played -> played.getResult() != null)
                .reduce((older, newer) -> newer)
                .filter(BelotDeal::isPlayedOut)
                .map(played -> new BelotTrickView(
                        played.getDealNumber(),
                        played.tricks().getLast().plays().stream()
                                .map(play -> new BelotPlayedCard(play.seat(), play.card()))
                                .toList(),
                        played.lastTrickWinner().orElse(null)))
                .orElse(null);
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
     * What the table has announced so far.
     *
     * <p>Each announcement appears at the moment it is made at a table, and not
     * before. A sequence or a carré is called out with the player's first card,
     * so it shows as soon as that player has played into the first trick —
     * the other three hear it then, not once the trick is over, which was when
     * this used to send them. Belote is said when the king or the queen of it
     * is laid down, so it shows once one of the two has been played; saying it
     * with the first card would tell the table two cards of a hand that has not
     * shown either.
     *
     * <p>While the hand is on only the kind goes out. Which of two terces is
     * the better one is settled once the cards are down, and the suit and top
     * card it is worked out from would name cards still in somebody's hand.
     */
    private BelotDeclarationsView declarationsFor(BelotGame table, BelotDeal deal) {
        List<BelotPlay> plays = deal.getPlays();
        if (deal.getContract() == null || plays.isEmpty()) {
            return null;
        }

        Map<Seat, List<Declaration>> bySeat = belotPlayService.declarationsBySeat(table, deal);
        boolean settled = deal.getStatus() != BelotDealStatus.PLAYING;

        int firstTrick = plays.getFirst().getTrickNo();
        Set<Seat> playedIntoFirstTrick = plays.stream()
                .filter(play -> play.getTrickNo() == firstTrick)
                .map(BelotPlay::getSeat)
                .collect(Collectors.toSet());

        List<BelotDeclarationView> shown = bySeat.entrySet().stream()
                .flatMap(held -> held.getValue().stream()
                        .filter(declaration -> settled
                                || hasBeenSaid(held.getKey(), declaration, playedIntoFirstTrick, plays))
                        .map(declaration -> settled
                                ? BelotDeclarationView.of(held.getKey(), declaration)
                                : BelotDeclarationView.called(held.getKey(), declaration)))
                .toList();

        if (!settled) {
            return new BelotDeclarationsView(shown, 0, 0);
        }

        return new BelotDeclarationsView(shown,
                belotPlayService.declarationPoints(bySeat, Team.NORTH_SOUTH),
                belotPlayService.declarationPoints(bySeat, Team.EAST_WEST));
    }

    /** Whether this seat has reached the point at which it says this out loud. */
    private static boolean hasBeenSaid(Seat seat, Declaration declaration,
                                       Set<Seat> playedIntoFirstTrick, List<BelotPlay> plays) {
        if (declaration.kind() == DeclarationKind.BELOTE) {
            return plays.stream().anyMatch(play -> play.getSeat() == seat
                    && play.getSuit() == declaration.suit()
                    && (play.getRank() == Rank.KING || play.getRank() == Rank.QUEEN));
        }
        return playedIntoFirstTrick.contains(seat);
    }
}
