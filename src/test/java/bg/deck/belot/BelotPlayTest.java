package bg.deck.belot;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.DealRounding;
import bg.deck.belot.engine.Dealing;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A hand played out through the tables it is stored in.
 *
 * <p>The engine is already fuzz-tested over two thousand deals; what is new
 * here is that the deal is rebuilt from rows between every single card. If the
 * tricks, the hands or whose turn it is were reconstructed wrongly, a deal
 * played this way would come apart — which is the point of playing one.
 */
@DisplayName("A belot hand, played out")
class BelotPlayTest {

    private final BelotDealRepository deals = mock(BelotDealRepository.class);
    private final BelotSeedService seeds = new BelotSeedService();
    private final BelotDealService dealService = new BelotDealService(deals, seeds);
    private final BelotStatsService statsService = mock(BelotStatsService.class);
    private final BelotPlayService play = new BelotPlayService(dealService, statsService);

    private BelotGame table;
    private BelotDeal deal;

    @BeforeEach
    void aTableMidDeal() {
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

    /** Bids the given contract and lets it stand, so the cards can be played. */
    private void contractOf(Contract contract) {
        Seat seat = deal.bidding().toAct();
        dealService.bid(deal, BidAction.bid(seat, contract));
        for (int i = 0; i < 3; i++) {
            seat = seat.next();
            dealService.bid(deal, BidAction.pass(seat));
        }
    }

    /** Plays the hand out, always taking the first card the engine allows. */
    private void playItOut() {
        while (!deal.isPlayedOut()) {
            Seat seat = play.toAct(deal);
            List<Card> legal = play.legalFor(table, deal, seat);
            assertFalse(legal.isEmpty(), seat + " has nothing legal to play");
            play.play(table, deal, seat, legal.getFirst());
        }
    }

    @Nested
    @DisplayName("the turn")
    class Turn {

        @Test
        @DisplayName("is the dealer's right hand to open")
        void whoLeadsTheFirstTrick() {
            contractOf(Contract.SPADES);

            assertEquals(Seat.NORTH.next(), play.toAct(deal));
        }

        @Test
        @DisplayName("goes round the table within a trick")
        void roundTheTable() {
            contractOf(Contract.SPADES);
            Seat leader = play.toAct(deal);

            play.play(table, deal, leader, play.legalFor(table, deal, leader).getFirst());

            assertEquals(leader.next(), play.toAct(deal));
        }

        @Test
        @DisplayName("and passes to whoever took the trick")
        void theWinnerLeads() {
            contractOf(Contract.SPADES);
            for (int i = 0; i < Seat.values().length; i++) {
                Seat seat = play.toAct(deal);
                play.play(table, deal, seat, play.legalFor(table, deal, seat).getFirst());
            }

            assertEquals(deal.lastTrickWinner().orElseThrow(), play.toAct(deal),
                    "the trick is rebuilt from its rows, and its winner leads the next");
            assertEquals(2, deal.currentTrickNumber());
        }

        @Test
        @DisplayName("belongs to one seat: nobody else may play")
        void outOfTurnIsRefused() {
            contractOf(Contract.SPADES);
            Seat waiting = play.toAct(deal).next();
            Card theirs = play.handOf(table, deal, waiting).getFirst();

            assertThrows(IllegalArgumentException.class, () -> play.play(table, deal, waiting, theirs));
            assertTrue(deal.getPlays().isEmpty(), "nothing was written");
        }

        @Test
        @DisplayName("and a card the player was never dealt is refused")
        void aCardFromNowhere() {
            contractOf(Contract.SPADES);
            Seat seat = play.toAct(deal);
            Card somebodyElses = play.handOf(table, deal, seat.next()).getFirst();

            assertThrows(IllegalArgumentException.class, () -> play.play(table, deal, seat, somebodyElses));
        }
    }

    @Nested
    @DisplayName("the hand")
    class Hand {

        @Test
        @DisplayName("is eight cards, and one shorter after every card played")
        void itShrinks() {
            contractOf(Contract.HEARTS);
            Seat seat = play.toAct(deal);
            assertEquals(Dealing.HAND_SIZE, play.handOf(table, deal, seat).size());

            Card card = play.legalFor(table, deal, seat).getFirst();
            play.play(table, deal, seat, card);

            List<Card> left = play.handOf(table, deal, seat);
            assertEquals(Dealing.HAND_SIZE - 1, left.size());
            assertFalse(left.contains(card), "a card is played once");
        }
    }

    @Nested
    @DisplayName("the end of it")
    class Scoring {

        /**
         * Every point is accounted for: the card points the two sides took,
         * rounded the way belot rounds them, are exactly what was written on
         * the sheet plus whatever is left hanging for the next deal. The same
         * invariant the self-play fuzz test holds the engine to — here it is
         * checked against a deal that went through the tables.
         *
         * <p>The contract’s own worth is asserted separately, as a floor: the
         * trick points are fixed, and declarations only ever add to them.
         */
        @ParameterizedTest(name = "{0}, worth at least {1}")
        @CsvSource({
                "SPADES,     162",
                "NO_TRUMPS,  260",
                "ALL_TRUMPS, 258",
        })
        void everyPointIsAccountedFor(Contract contract, int worth) {
            contractOf(contract);
            playItOut();

            assertEquals(BelotDealStatus.FINISHED, deal.getStatus());

            int raw = deal.getCallerPoints() + deal.getOpponentPoints();
            assertTrue(raw >= worth,
                    contract + " is worth " + worth + " in tricks, and declarations only add: " + raw);

            assertEquals(DealRounding.nearest(raw),
                    table.getNorthSouthScore() + table.getEastWestScore() + table.getHangingPoints(),
                    "the deal went onto the sheet, or is hanging for the next hand");
            assertEquals(deal.getCallerScore() + deal.getOpponentScore(),
                    table.getNorthSouthScore() + table.getEastWestScore(),
                    "the sheet holds what the deal says it awarded");
        }

        @Test
        @DisplayName("thirty-two cards are played, eight tricks of four")
        void everyCardIsPlayed() {
            contractOf(Contract.CLUBS);
            playItOut();

            assertEquals(32, deal.getPlays().size());
            assertEquals(Dealing.HAND_SIZE, deal.tricks().size());
            deal.tricks().forEach(trick -> assertEquals(4, trick.plays().size()));
        }

        @Test
        @DisplayName("and nobody is left holding a card")
        void everyHandIsEmpty() {
            contractOf(Contract.DIAMONDS);
            playItOut();

            for (Seat seat : Seat.values()) {
                assertTrue(play.handOf(table, deal, seat).isEmpty(), seat + " played everything");
            }
        }
    }
}
