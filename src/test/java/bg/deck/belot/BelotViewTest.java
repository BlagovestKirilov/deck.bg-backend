package bg.deck.belot;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.DeclarationKind;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.model.response.BelotDeclarationView;
import bg.deck.belot.model.response.BelotPlayedCard;
import bg.deck.belot.model.response.BelotStateResponse;
import bg.deck.belot.model.response.BelotTrickView;
import bg.deck.belot.repository.BelotDealRepository;
import bg.deck.belot.service.BelotDealService;
import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.service.BelotPlayService;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotService;
import bg.deck.belot.service.BelotTableService;
import bg.deck.belot.service.BelotTurnService;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.service.WebSocketService;
import bg.deck.belot.engine.BidKind;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotCutRequest;
import bg.deck.belot.model.request.BelotPlayRequest;
import bg.deck.common.exception.IllegalMoveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.time.Duration;
import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What each of the four is told.
 *
 * <p>The whole reason belot pushes a separate message per seat rather than one
 * per table is that a hand is private. A leak here would not look like a bug to
 * the player who benefits from it — it would look like the game is rigged — so
 * it is checked rather than assumed.
 */
@DisplayName("A seat's view of the table")
class BelotViewTest {

    private static final List<String> PLAYERS = List.of("petko91", "ninja2011", "gosho", "ivan");

    private final BelotDealRepository deals = mock(BelotDealRepository.class);
    private final BelotSeedService seeds = new BelotSeedService();
    private final BelotDealService dealService = new BelotDealService(deals, seeds);
    private final BelotStatsService statsService = mock(BelotStatsService.class);
    private final BelotPlayService playService = new BelotPlayService(dealService, statsService);
    private final BelotTurnService turnService = new BelotTurnService(dealService, playService,
            new BelotProperties(Duration.ofSeconds(45),
                    Duration.ofMillis(1600), Duration.ofSeconds(8), Duration.ZERO, Duration.ZERO));

    private final BelotTableService tables = mock(BelotTableService.class);
    private final BelotPlayerService players = mock(BelotPlayerService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final WebSocketService sockets = mock(WebSocketService.class);

    private final BelotService belot =
            new BelotService(tables, dealService, playService, turnService, players, statsService,
                    availability, sockets, event -> { });

    private BelotGame table;
    private BelotDeal deal;

    @BeforeEach
    void aDealInProgress() {
        seatTheTable();
    }

    private void seatTheTable() {
        table = new BelotGame();
        // The database hands out ids, and there is no database here — but the
        // push is addressed by table id, so the table needs one.
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
        table.setServerSeed(seeds.newSeed());
        table.setServerSeedHash(seeds.hash(table.getServerSeed()));
        table.setDealerSeat(Seat.NORTH);

        Seat seat = Seat.NORTH;
        for (String player : PLAYERS) {
            table.add(new BelotSeat(seat, player));
            seat = seat.next();
        }

        when(deals.save(any(BelotDeal.class))).thenAnswer(call -> call.getArgument(0));
        when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.empty());
        deal = dealService.dealNext(table);
        when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.of(deal));

        PLAYERS.forEach(player -> when(tables.tableOf(player)).thenReturn(Optional.of(table)));
    }

    @Test
    @DisplayName("is sent to a player at the table, and a player at none is told there is nothing")
    void onlyAPlayerAtATableHasAView() {
        assertTrue(belot.sendState(PLAYERS.getFirst()));
        assertFalse(belot.sendState("passer-by"), "nobody of that name is sitting anywhere");
    }

    private BelotStateResponse viewSentTo(String username) {
        ArgumentCaptor<Object> state = ArgumentCaptor.forClass(Object.class);
        belot.sendState(username);
        verify(sockets).notifyBelotUpdate(
                org.mockito.ArgumentMatchers.eq(username),
                state.capture());
        return (BelotStateResponse) state.getValue();
    }

    @Test
    @DisplayName("holds that player's cards and nobody else's")
    void everyHandIsItsOwners() {
        Map<Seat, List<Card>> dealt = dealService.hands(table, deal);

        for (String player : PLAYERS) {
            BelotStateResponse view = viewSentTo(player);
            Seat seat = table.seatOf(player).orElseThrow().getSeat();

            assertEquals(Dealing.beforeBidding(dealt.get(seat)), view.yourHand(),
                    player + " is shown their own five");

            Set<Card> others = new HashSet<>();
            for (Seat other : Seat.values()) {
                if (other != seat) {
                    others.addAll(dealt.get(other));
                }
            }
            assertTrue(view.yourHand().stream().noneMatch(others::contains),
                    "nothing in " + player + "'s view belongs to another seat");
        }
    }

    @Test
    @DisplayName("names everyone at the table, and which seat is theirs")
    void theTableIsPublic() {
        BelotStateResponse view = viewSentTo("gosho");

        assertEquals(4, view.seats().size());
        assertEquals(table.seatOf("gosho").orElseThrow().getSeat(), view.yourSeat());
        assertEquals(table.getServerSeedHash(), view.serverSeedHash(),
                "the commitment is shown before a card is played, not after");
    }

    @Test
    @DisplayName("offers calls only to the player whose turn it is")
    void onlyTheSpeakerIsOfferedCalls() {
        Seat toAct = deal.bidding().toAct();
        String speaking = table.getSeats().stream()
                .filter(seat -> seat.getSeat() == toAct)
                .map(BelotSeat::getUsername)
                .findFirst()
                .orElseThrow();

        BelotStateResponse theirs = viewSentTo(speaking);
        assertNotNull(theirs.bidding());
        assertFalse(theirs.bidding().yours().isEmpty(), "they can pass at the very least");

        for (String waiting : PLAYERS) {
            if (!waiting.equals(speaking)) {
                assertTrue(viewSentTo(waiting).bidding().yours().isEmpty(),
                        waiting + " is not being offered a call out of turn");
            }
        }
    }

    @Test
    @DisplayName("shows what has been said to everyone")
    void theBiddingIsHeardByAll() {
        Seat toAct = deal.bidding().toAct();
        dealService.bid(deal, BidAction.pass(toAct));

        List<String> heard = new ArrayList<>();
        for (String player : PLAYERS) {
            if (!viewSentTo(player).bidding().said().isEmpty()) {
                heard.add(player);
            }
        }
        assertEquals(PLAYERS, heard, "a pass is said out loud");
    }

    @Test
    @DisplayName("keeps a finished trick on the table until somebody leads")
    void theFinishedTrickStaysOut() {
        // A contract, so there are cards to play rather than calls to make.
        Seat bidder = deal.bidding().toAct();
        dealService.bid(deal, BidAction.bid(bidder, Contract.SPADES));
        for (int i = 0; i < 3; i++) {
            bidder = bidder.next();
            dealService.bid(deal, BidAction.pass(bidder));
        }

        for (int card = 0; card < 4; card++) {
            Seat seat = playService.toAct(deal);
            playService.play(table, deal, seat, playService.legalFor(table, deal, seat).getFirst());
        }

        BelotStateResponse view = viewSentTo("petko91");
        assertNotNull(view.play());
        assertEquals(4, view.play().onTable().size(),
                "the four cards stay out: a player must see the trick they played into");
        assertNotNull(view.play().wonBy(), "and who took it");
        assertEquals(1, view.play().trickNo(), "it is still the first trick until the next is led");
    }

    @Test
    @DisplayName("holds the bidding until the player on the dealer's left has cut")
    void nobodyBidsBeforeTheCut() {
        belotDealServiceAwaitsCut();
        Seat first = deal.bidding().toAct();

        assertThrows(IllegalMoveException.class,
                () -> belot.bid(nameAt(first), new BelotBidRequest(BidKind.PASS, null)),
                "the deck has not been cut");
        assertEquals(deal.cutter(), turnService.toAct(deal).orElseThrow(), "it is the cutter's turn");
        assertNull(viewNowFor(PLAYERS.getFirst()).cutAt(), "and every screen is told it is waiting for the cut");

        Seat notTheCutter = deal.cutter().next();
        assertThrows(IllegalMoveException.class,
                () -> belot.cut(nameAt(notTheCutter), new BelotCutRequest(12)),
                "only the player on the dealer's left cuts");

        belot.cut(nameAt(deal.cutter()), new BelotCutRequest(12));
        assertEquals(12, viewNowFor(PLAYERS.getFirst()).cutAt(), "every screen is told where it was cut");
        belot.bid(nameAt(first), new BelotBidRequest(BidKind.PASS, null));
        assertEquals(1, deal.getBids().size(), "and the bidding opens");
    }

    @Test
    @DisplayName("cuts for the player on the dealer's left when they let the clock run out")
    void anUncutDeckIsCutForThem() {
        belotDealServiceAwaitsCut();
        deal.setTurnStartedAt(Instant.now().minusSeconds(9));

        belot.timeUp("gosho");

        assertNotNull(deal.getCutAt(), "the deck was cut for them");
        assertTrue(deal.getBids().isEmpty(), "and nobody was passed for: the bidding has only just opened");
    }

    /** As the table holds every hand it deals for real. */
    private void belotDealServiceAwaitsCut() {
        dealService.awaitCut(deal, Instant.now());
    }

    @Test
    @DisplayName("says пас for a seat that has nothing else it may say")
    void aSeatWithNoChoiceIsPassedFor() {
        Seat first = deal.bidding().toAct();
        belot.bid(nameAt(first), new BelotBidRequest(BidKind.BID, Contract.ALL_TRUMPS));
        Seat second = deal.bidding().toAct();
        belot.bid(nameAt(second), new BelotBidRequest(BidKind.CONTRA, null));
        Seat third = deal.bidding().toAct();
        belot.bid(nameAt(third), new BelotBidRequest(BidKind.RECONTRA, null));

        // Over a recontra on всичко коз there is no call left but пас, for
        // anybody: the other three are passed for and the hand is played.
        assertEquals(BelotDealStatus.PLAYING, deal.getStatus(),
                "nobody had anything left to say: " + deal.getBids().size() + " calls");
        assertEquals(6, deal.getBids().size(), "three calls made and three passes said for them");
    }

    private String nameAt(Seat seat) {
        return table.getSeats().stream()
                .filter(taken -> taken.getSeat() == seat)
                .map(BelotSeat::getUsername)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("acts for a seat as soon as a screen says its clock ran out, and not before")
    void aClockAtNoughtIsActedOnAtOnce() {
        deal.setTurnStartedAt(Instant.now());
        belot.timeUp("gosho");
        assertTrue(deal.getBids().isEmpty(), "the server's clock has not run out, so nothing is done");

        deal.setTurnStartedAt(Instant.now().minusSeconds(46));
        belot.timeUp("gosho");
        assertEquals(1, deal.getBids().size(), "the seat that ran out is passed for, without waiting for the sweep");
    }

    @Test
    @DisplayName("refuses the next lead while the last trick is still being taken")
    void theNextLeadWaitsForTheTrickToBeTaken() {
        Seat bidder = deal.bidding().toAct();
        dealService.bid(deal, BidAction.bid(bidder, Contract.SPADES));
        for (int i = 0; i < 3; i++) {
            bidder = bidder.next();
            dealService.bid(deal, BidAction.pass(bidder));
        }
        for (int card = 0; card < 4; card++) {
            Seat seat = playService.toAct(deal);
            playService.play(table, deal, seat, playService.legalFor(table, deal, seat).getFirst());
        }

        Seat leader = playService.toAct(deal);
        String leading = table.getSeats().stream()
                .filter(seat -> seat.getSeat() == leader)
                .map(BelotSeat::getUsername)
                .findFirst()
                .orElseThrow();
        Card lead = playService.legalFor(table, deal, leader).getFirst();

        assertThrows(IllegalMoveException.class,
                () -> belot.play(leading, new BelotPlayRequest(lead)),
                "nobody has seen who took the trick yet");
        assertEquals(4, deal.getPlays().size(), "and nothing was played");

        // Once the trick has been swept, the same lead goes through.
        deal.setTurnStartedAt(Instant.now().minusSeconds(2));
        belot.play(leading, new BelotPlayRequest(lead));
        assertEquals(5, deal.getPlays().size());
    }

    @Test
    @DisplayName("shows the last trick of a hand, which the next deal would otherwise hide")
    void theLastTrickOfAHandIsSeen() {
        Seat bidder = deal.bidding().toAct();
        dealService.bid(deal, BidAction.bid(bidder, Contract.SPADES));
        for (int i = 0; i < 3; i++) {
            bidder = bidder.next();
            dealService.bid(deal, BidAction.pass(bidder));
        }
        when(deals.findByGameOrderByDealNumberAsc(table)).thenReturn(List.of(deal));

        for (int card = 0; card < 31; card++) {
            Seat seat = playService.toAct(deal);
            playService.play(table, deal, seat, playService.legalFor(table, deal, seat).getFirst());
        }
        assertNull(viewNowFor("petko91").lastTrick(), "nothing to show while the hand is still on");

        Seat last = playService.toAct(deal);
        playService.play(table, deal, last, playService.legalFor(table, deal, last).getFirst());

        BelotTrickView shown = viewNowFor("petko91").lastTrick();
        assertNotNull(shown, "the card that ends the hand is seen to fall");
        assertEquals(deal.getDealNumber(), shown.dealNumber());
        assertEquals(deal.tricks().getLast().plays().stream()
                        .map(play -> new BelotPlayedCard(play.seat(), play.card()))
                        .toList(),
                shown.cards(), "all four of the last trick, in the order they fell");
        assertEquals(deal.lastTrickWinner().orElseThrow(), shown.wonBy(), "and who took it");
        assertEquals(List.of(), viewNowFor("petko91").yourHand(),
                "a hand that is over leaves nothing in front of anybody");
    }
    /** A fresh look for this player, however many they have been sent already. */
    private BelotStateResponse viewNowFor(String username) {
        clearInvocations(sockets);
        return viewSentTo(username);
    }

    /** What the table has heard from one seat, as that seat's views show it. */
    private List<BelotDeclarationView> heardFrom(Seat seat) {
        BelotStateResponse view = viewNowFor(PLAYERS.getFirst());
        if (view.declarations() == null) {
            return List.of();
        }
        return view.declarations().shown().stream().filter(heard -> heard.seat() == seat).toList();
    }

    @Test
    @DisplayName("hears an announcement as its player plays their first card, and only what was said")
    void anAnnouncementIsHeardWithTheFirstCard() {
        // A hand in which the player leading the first trick holds a sequence
        // or a carré. The leader, because theirs is the announcement that has
        // the most of the trick still to wait through: announced by the last
        // of the four, it would be heard once the trick is over either way.
        // Deals are random, so deal until the leader has one.
        Seat announcer = null;
        for (int attempt = 0; attempt < 200 && announcer == null; attempt++) {
            if (attempt > 0) {
                seatTheTable();
            }
            Seat bidder = deal.bidding().toAct();
            dealService.bid(deal, BidAction.bid(bidder, Contract.SPADES));
            for (int i = 0; i < 3; i++) {
                bidder = bidder.next();
                dealService.bid(deal, BidAction.pass(bidder));
            }

            Seat leader = playService.toAct(deal);
            List<Declaration> held = playService.declarationsBySeat(table, deal).get(leader);
            if (held != null && held.stream().anyMatch(each -> each.kind() != DeclarationKind.BELOTE)) {
                announcer = leader;
            }
        }
        assertNotNull(announcer, "two hundred deals and no leader with a sequence or carré");

        // Round the first trick, one card at a time.
        boolean announcerHasPlayed = false;
        for (int card = 0; card < 4; card++) {
            Seat seat = playService.toAct(deal);

            if (!announcerHasPlayed) {
                assertTrue(heardFrom(announcer).stream().noneMatch(each -> each.kind() != DeclarationKind.BELOTE),
                        announcer + " has said nothing yet, so the table has heard nothing");
            }

            playService.play(table, deal, seat, playService.legalFor(table, deal, seat).getFirst());
            if (seat == announcer) {
                announcerHasPlayed = true;
                List<BelotDeclarationView> heard = heardFrom(announcer).stream()
                        .filter(each -> each.kind() != DeclarationKind.BELOTE)
                        .toList();

                assertFalse(heard.isEmpty(),
                        "the other three hear it with " + announcer + "'s first card, not once the trick is over");
                heard.forEach(each -> {
                    assertNull(each.suit(), "the suit is not said: it would name cards still in the hand");
                    assertNull(each.topRank(), "nor the card it runs up to");
                    assertEquals(0, each.points(), "and whose is worth more is settled when the hand is over");
                });
            }
        }
        assertTrue(announcerHasPlayed, "every seat plays into the first trick");
    }
}
