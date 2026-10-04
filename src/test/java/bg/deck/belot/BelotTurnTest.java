package bg.deck.belot;

import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotDealRepository;
import bg.deck.belot.service.BelotDealService;
import bg.deck.belot.service.BelotPlayService;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotTurnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The turn clock, and what the table does when it runs out.
 *
 * <p>A player who walks away must not be able to hold three others at a table
 * for ever, and must not lose the hand for their partner either. What happens
 * instead is the least consequential legal thing — and that is a decision
 * worth a test, because it is the one a player will complain about.
 */
@DisplayName("A seat that stops answering")
class BelotTurnTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final BelotDealRepository deals = mock(BelotDealRepository.class);
    private final BelotSeedService seeds = new BelotSeedService();
    private final BelotDealService dealService = new BelotDealService(deals, seeds);
    private final BelotStatsService statsService = mock(BelotStatsService.class);
    private final BelotPlayService playService = new BelotPlayService(dealService, statsService);
    private final BelotTurnService turns = new BelotTurnService(
            dealService, playService, new BelotProperties(TIMEOUT, Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ZERO));

    private BelotGame table;
    private BelotDeal deal;

    @BeforeEach
    void aDealWaitingOnSomebody() {
        table = new BelotGame();
        table.setServerSeed(seeds.newSeed());
        table.setServerSeedHash(seeds.hash(table.getServerSeed()));
        table.setDealerSeat(Seat.NORTH);

        Seat seat = Seat.NORTH;
        for (String player : List.of("petko91", "ninja2011", "gosho", "ivan")) {
            table.add(new BelotSeat(seat, player));
            seat = seat.next();
        }

        when(deals.save(any(BelotDeal.class))).thenAnswer(call -> call.getArgument(0));
        when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.empty());
        deal = dealService.dealNext(table);
        when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.of(deal));
    }

    /** Puts the turn's start far enough back that it has run out. */
    private void timeIsUp() {
        deal.setTurnStartedAt(Instant.now().minus(TIMEOUT).minusSeconds(1));
    }

    private void contractOf(Contract contract) {
        Seat seat = deal.bidding().toAct();
        dealService.bid(deal, BidAction.bid(seat, contract));
        for (int i = 0; i < 3; i++) {
            seat = seat.next();
            dealService.bid(deal, BidAction.pass(seat));
        }
    }

    @Nested
    @DisplayName("the clock")
    class Clock {

        @Test
        @DisplayName("starts when the hand is dealt")
        void startsWithTheDeal() {
            assertTrue(deal.getTurnStartedAt() != null, "a dealt hand is already waiting on somebody");
            assertEquals(deal.getTurnStartedAt().plus(TIMEOUT), turns.deadline(deal).orElseThrow());
        }

        @Test
        @DisplayName("restarts with every turn taken")
        void restartsEachTurn() {
            // Wound back rather than read twice: two calls to the clock in the
            // same millisecond are equal, and a test that depends on them not
            // being equal fails for a reason that has nothing to do with belot.
            Instant longAgo = Instant.now().minusSeconds(600);
            deal.setTurnStartedAt(longAgo);

            dealService.bid(deal, BidAction.pass(deal.bidding().toAct()));

            assertTrue(deal.getTurnStartedAt().isAfter(longAgo),
                    "the next seat's time is their own, not what is left of somebody else's");
        }

        @Test
        @DisplayName("stops when there is nobody to wait for")
        void noTurnNoDeadline() {
            deal.setStatus(BelotDealStatus.FINISHED);

            assertTrue(turns.toAct(deal).isEmpty());
            assertTrue(turns.deadline(deal).isEmpty(), "a finished hand is waiting on nobody");
        }
    }

    @Nested
    @DisplayName("while the bidding is on")
    class Bidding {

        @Test
        @DisplayName("the table passes for them")
        void passesForThem() {
            Seat absent = deal.bidding().toAct();
            timeIsUp();

            assertTrue(turns.actForAbsentPlayer(table, deal));

            assertEquals(1, deal.getBids().size());
            assertEquals(absent, deal.getBids().getFirst().getSeat());
            assertEquals(absent.next(), deal.bidding().toAct(), "and the turn moves on");
        }

        @Test
        @DisplayName("a pass, which commits their partner to nothing")
        void nothingIsBidForThem() {
            timeIsUp();
            turns.actForAbsentPlayer(table, deal);

            assertTrue(deal.bidding().contract().isEmpty(),
                    "nobody has their side play a contract they did not choose");
        }
    }

    @Nested
    @DisplayName("while the hand is being played")
    class Playing {

        @Test
        @DisplayName("the table plays a legal card for them")
        void playsForThem() {
            contractOf(Contract.SPADES);
            Seat absent = playService.toAct(deal);
            timeIsUp();

            assertTrue(turns.actForAbsentPlayer(table, deal));

            assertEquals(1, deal.getPlays().size());
            assertEquals(absent, deal.getPlays().getFirst().getSeat());
            assertEquals(7, playService.handOf(table, deal, absent).size(), "the card left their hand");
        }
    }

    @Nested
    @DisplayName("and until it runs out")
    class BeforeTheDeadline {

        @Test
        @DisplayName("nothing is done for anyone")
        void patienceFirst() {
            assertFalse(turns.actForAbsentPlayer(table, deal),
                    "a player who is thinking is not a player who has gone");
            assertTrue(deal.getBids().isEmpty());
        }

        @Test
        @DisplayName("and a hand nobody is waiting on is left alone")
        void finishedHandsAreNotTouched() {
            deal.setStatus(BelotDealStatus.FINISHED);
            timeIsUp();

            assertFalse(turns.actForAbsentPlayer(table, deal));
        }
    }
}
