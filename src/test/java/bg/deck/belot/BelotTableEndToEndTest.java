package bg.deck.belot;

import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.engine.BidKind;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotPlayRequest;
import bg.deck.belot.service.BelotDealService;
import bg.deck.belot.service.BelotPlayService;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.belot.service.BelotService;
import bg.deck.belot.service.BelotTableService;
import bg.deck.belot.service.BelotTurnService;
import bg.deck.service.AvailabilityService;
import bg.deck.service.WebSocketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four people sit down and play a hand, through the endpoints' own services
 * and a real database.
 *
 * <p>The nearest thing to the checkpoint in {@code docs/belot/BUILD.md} that
 * can run unattended: matchmaking, a deal, a bidding that settles, eight
 * tricks, a score written, and the next hand dealt. Each piece has a test of
 * its own; what this catches is the joins between them, which is where a
 * table that works in pieces stops working as a table.
 */
@DisplayName("A table of four, from sitting down to the next hand")
@DataJpaTest
// A @ConfigurationProperties record is bound, not imported: @Import would
// ask Spring to construct it, and it has no bean to give the Duration to.
@EnableConfigurationProperties(BelotProperties.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotService.class, BelotTableService.class, BelotDealService.class, BelotPlayService.class,
        BelotTurnService.class, BelotPlayerService.class, BelotSeedService.class, BelotStatsService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotendtoend;INIT=CREATE SCHEMA IF NOT EXISTS belot",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotTableEndToEndTest {

    private static final List<String> PLAYERS = List.of("petko91", "ninja2011", "gosho", "ivan");

    /** Belot is on offer; who it is on offer to is AvailabilityServiceTest's business. */
    @MockitoBean private AvailabilityService availabilityService;
    /** Nobody is listening: what is pushed is BelotViewTest's business. */
    @MockitoBean private WebSocketService webSocketService;

    @Autowired private BelotService belot;
    @Autowired private BelotTableService tables;
    @Autowired private BelotDealService deals;
    @Autowired private BelotPlayService plays;
    @Autowired private BelotTurnService turns;

    private BelotGame seatFour() {
        PLAYERS.forEach(belot::search);
        return tables.tableOf(PLAYERS.getFirst()).orElseThrow();
    }

    private BelotDeal deal(BelotGame table) {
        return deals.current(table).orElseThrow();
    }

    /** Whoever is to act, by the name they sat down under. */
    private String whoseTurn(BelotGame table) {
        Seat seat = turns.toAct(deal(table)).orElseThrow();
        return table.getSeats().stream()
                .filter(taken -> taken.getSeat() == seat)
                .findFirst()
                .orElseThrow()
                .getUsername();
    }

    /** Settles a contract: the first seat bids, the other three pass. */
    private void bidItUp(BelotGame table) {
        belot.bid(whoseTurn(table), new BelotBidRequest(BidKind.BID, bg.deck.belot.engine.Contract.SPADES));
        for (int i = 0; i < 3; i++) {
            belot.bid(whoseTurn(table), new BelotBidRequest(BidKind.PASS, null));
        }
    }

    @Test
    @DisplayName("the fourth to arrive starts the hand")
    void fourSitDownAndAreDealt() {
        BelotGame table = seatFour();

        assertEquals(BelotGameStatus.PLAYING, table.getStatus());
        assertEquals(4, table.getSeats().size());
        assertEquals(1, deal(table).getDealNumber());
        assertEquals(BelotDealStatus.BIDDING, deal(table).getStatus());

        // visibleHand, not handOf: the eight are dealt from the seed at once,
        // and what a player may see of them during the bidding is five.
        PLAYERS.forEach(player -> assertEquals(5,
                deals.visibleHand(table, deal(table), table.seatOf(player).orElseThrow().getSeat()).size(),
                player + " is looking at five cards while the bidding is on"));
    }

    @Test
    @DisplayName("a hand is bid for, played out, and the next one dealt")
    void aWholeHand() {
        BelotGame table = seatFour();
        bidItUp(table);

        BelotDeal first = deal(table);
        assertEquals(BelotDealStatus.PLAYING, first.getStatus());
        assertNotNull(first.getContract());

        while (!first.isPlayedOut()) {
            String player = whoseTurn(table);
            Seat seat = table.seatOf(player).orElseThrow().getSeat();
            List<Card> legal = plays.legalFor(table, first, seat);
            assertFalse(legal.isEmpty(), player + " has something legal to play");

            belot.play(player, new BelotPlayRequest(legal.getFirst()));
        }

        assertEquals(BelotDealStatus.FINISHED, first.getStatus());
        assertNotNull(first.getResult(), "the hand was counted");
        assertTrue(table.getNorthSouthScore() + table.getEastWestScore() + table.getHangingPoints() > 0,
                "and the points went onto the sheet");

        BelotDeal next = deal(table);
        assertEquals(2, next.getDealNumber(), "the next hand is dealt without anybody asking");
        assertEquals(first.getDealerSeat().next(), next.getDealerSeat(), "and the deal moves one seat along");
    }

    @Test
    @DisplayName("a hand nobody wants is thrown in and dealt again")
    void fourPassesRedeal() {
        BelotGame table = seatFour();

        for (int i = 0; i < 4; i++) {
            belot.bid(whoseTurn(table), new BelotBidRequest(BidKind.PASS, null));
        }

        BelotDeal next = deal(table);
        assertEquals(2, next.getDealNumber(), "the hand was thrown in and another dealt");
        assertEquals(BelotDealStatus.BIDDING, next.getStatus());
        assertEquals(0, table.getNorthSouthScore() + table.getEastWestScore(),
                "nothing is scored for a hand nobody bid");
    }

    @Test
    @DisplayName("somebody already at a table is put back at it, not at another")
    void searchingTwiceKeepsTheSeat() {
        BelotGame table = seatFour();

        belot.search("gosho");

        assertEquals(table.getId(), tables.tableOf("gosho").orElseThrow().getId());
        assertEquals(4, table.getSeats().size(), "and nobody is seated twice");
    }
}
