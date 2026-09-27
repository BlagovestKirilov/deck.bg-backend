package bg.deck.belot;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.model.response.BelotStateResponse;
import bg.deck.belot.repository.BelotDealRepository;
import bg.deck.belot.service.BelotDealService;
import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.service.BelotPlayService;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotService;
import bg.deck.belot.service.BelotTableService;
import bg.deck.belot.service.BelotTurnService;
import bg.deck.service.AvailabilityService;
import bg.deck.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private final BelotPlayService playService = new BelotPlayService(dealService);
    private final BelotTurnService turnService = new BelotTurnService(dealService, playService,
            new BelotProperties(Duration.ofSeconds(45), Duration.ofSeconds(10), Duration.ofMinutes(1)));

    private final BelotTableService tables = mock(BelotTableService.class);
    private final BelotPlayerService players = mock(BelotPlayerService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final WebSocketService sockets = mock(WebSocketService.class);

    private final BelotService belot =
            new BelotService(tables, dealService, playService, turnService, players, availability, sockets);

    private BelotGame table;
    private BelotDeal deal;

    @BeforeEach
    void aDealInProgress() {
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
}
