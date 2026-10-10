package bg.deck.belot;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Card;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.enums.Doubling;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.enums.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotDealRepository;
import bg.deck.belot.service.BelotDealService;
import bg.deck.belot.service.BelotSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A hand: dealt from the seed, bid over, and left in the state the bidding put
 * it in.
 *
 * <p>The repository is a stand-in here — what is under test is the lifecycle,
 * not the persistence, and {@link BelotTableTest} already runs the schema
 * against a real database.
 */
@DisplayName("A belot deal")
class BelotDealTest {

    private final BelotDealRepository deals = mock(BelotDealRepository.class);
    private final BelotSeedService seeds = new BelotSeedService();
    private final BelotDealService service = new BelotDealService(deals, seeds);

    private BelotGame table;

    @BeforeEach
    void aTableOfFour() {
        table = new BelotGame();
        table.setServerSeed(seeds.newSeed());
        table.setServerSeedHash(seeds.hash(table.getServerSeed()));
        table.setDealerSeat(Seat.NORTH);
        table.add(new BelotSeat(Seat.NORTH, "petko91"));
        table.add(new BelotSeat(Seat.WEST, "ninja2011"));
        table.add(new BelotSeat(Seat.SOUTH, "gosho"));
        table.add(new BelotSeat(Seat.EAST, "ivan"));

        when(deals.save(any(BelotDeal.class))).thenAnswer(call -> call.getArgument(0));
        when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.empty());
    }

    private BelotDeal firstDeal() {
        return service.dealNext(table);
    }

    @Nested
    @DisplayName("dealing")
    class DealingHands {

        @Test
        @DisplayName("the first hand is dealt by whoever the table named")
        void theFirstDealer() {
            BelotDeal deal = firstDeal();

            assertEquals(1, deal.getDealNumber());
            assertEquals(Seat.NORTH, deal.getDealerSeat());
            assertEquals(BelotDealStatus.BIDDING, deal.getStatus());
        }

        @Test
        @DisplayName("and the next moves one seat along")
        void theDealMoves() {
            BelotDeal first = firstDeal();
            when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.of(first));

            BelotDeal second = service.dealNext(table);

            assertEquals(2, second.getDealNumber());
            assertEquals(Seat.NORTH.next(), second.getDealerSeat());
        }

        @Test
        @DisplayName("eight cards each, and all thirty-two of them")
        void everybodyGetsAHand() {
            Map<Seat, List<Card>> hands = service.hands(table, firstDeal());

            Set<Card> all = new HashSet<>();
            for (Seat seat : Seat.values()) {
                assertEquals(Dealing.HAND_SIZE, hands.get(seat).size(), seat + " holds eight");
                all.addAll(hands.get(seat));
            }
            assertEquals(32, all.size(), "no card is dealt twice");
        }

        @Test
        @DisplayName("the same hand however often it is asked for")
        void theShuffleIsTheSeed() {
            BelotDeal deal = firstDeal();

            assertEquals(service.hands(table, deal), service.hands(table, deal),
                    "a hand is derived from the seed, so it cannot come out differently");
        }

        @Test
        @DisplayName("and a different one next deal")
        void eachDealIsItsOwn() {
            BelotDeal first = firstDeal();
            when(deals.findFirstByGameOrderByDealNumberDesc(table)).thenReturn(Optional.of(first));
            BelotDeal second = service.dealNext(table);

            assertNotEquals(service.hands(table, first), service.hands(table, second));
        }

        @Test
        @DisplayName("five cards are visible while the bidding is on, eight after it")
        void whatABidderSees() {
            BelotDeal deal = firstDeal();

            assertEquals(Dealing.BEFORE_BIDDING, service.visibleHand(table, deal, Seat.WEST).size());

            deal.setStatus(BelotDealStatus.PLAYING);
            assertEquals(Dealing.HAND_SIZE, service.visibleHand(table, deal, Seat.WEST).size());
        }

        @Test
        @DisplayName("and the five are the first five of the eight")
        void theVisibleFiveAreTheirOwn() {
            BelotDeal deal = firstDeal();
            List<Card> whole = service.hands(table, deal).get(Seat.WEST);

            assertEquals(whole.subList(0, Dealing.BEFORE_BIDDING), service.visibleHand(table, deal, Seat.WEST));
        }
    }

    @Nested
    @DisplayName("bidding")
    class BiddingOverIt {

        @Test
        @DisplayName("is opened by the seat to the dealer's right")
        void whoSpeaksFirst() {
            BelotDeal deal = firstDeal();

            assertEquals(Seat.NORTH.next(), deal.bidding().toAct());
        }

        @Test
        @DisplayName("records what was said, in order")
        void theTurnsAreKept() {
            BelotDeal deal = firstDeal();
            Seat first = deal.bidding().toAct();

            service.bid(deal, BidAction.pass(first));
            service.bid(deal, BidAction.bid(first.next(), Contract.HEARTS));

            assertEquals(2, deal.getBids().size());
            assertEquals(Contract.HEARTS, deal.bidding().highestBid());
            assertEquals(first.next(), deal.bidding().bidder());
        }

        @Test
        @DisplayName("out of turn is refused, and nothing is written")
        void outOfTurnIsRefused() {
            BelotDeal deal = firstDeal();
            Seat notTheirTurn = deal.bidding().toAct().next();

            assertThrows(IllegalArgumentException.class,
                    () -> service.bid(deal, BidAction.pass(notTheirTurn)));
            assertEquals(0, deal.getBids().size());
        }

        @Test
        @DisplayName("a bid and three passes settle the contract")
        void threePassesEndIt() {
            BelotDeal deal = firstDeal();
            Seat seat = deal.bidding().toAct();

            service.bid(deal, BidAction.bid(seat, Contract.SPADES));
            Seat bidder = seat;
            for (int i = 0; i < 3; i++) {
                seat = seat.next();
                service.bid(deal, BidAction.pass(seat));
            }

            assertEquals(BelotDealStatus.PLAYING, deal.getStatus());
            assertEquals(Contract.SPADES, deal.getContract());
            assertEquals(bidder, deal.getDeclarerSeat());
            assertEquals(Doubling.NONE, deal.getDoubling());
        }

        @Test
        @DisplayName("a contra is carried onto the deal")
        void contraIsRecorded() {
            BelotDeal deal = firstDeal();
            Seat seat = deal.bidding().toAct();

            service.bid(deal, BidAction.bid(seat, Contract.ALL_TRUMPS));
            service.bid(deal, BidAction.contra(seat.next()));
            service.bid(deal, BidAction.pass(seat.next().next()));
            service.bid(deal, BidAction.pass(seat.next().next().next()));
            service.bid(deal, BidAction.pass(seat));

            assertEquals(BelotDealStatus.PLAYING, deal.getStatus());
            assertEquals(Doubling.CONTRA, deal.getDoubling());
        }

        @Test
        @DisplayName("four passes throw the hand in, with no contract")
        void fourPassesThrowItIn() {
            BelotDeal deal = firstDeal();
            Seat seat = deal.bidding().toAct();

            List<Seat> all = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                all.add(seat);
                seat = seat.next();
            }
            all.forEach(who -> service.bid(deal, BidAction.pass(who)));

            assertEquals(BelotDealStatus.THROWN_IN, deal.getStatus());
            assertNull(deal.getContract());
            assertNull(deal.getDeclarerSeat());
        }
    }
}
