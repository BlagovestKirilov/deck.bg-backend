package bg.deck.belot;

import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.enums.BidKind;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.enums.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.enums.BelotGameStatus;
import bg.deck.belot.model.BelotPlayerStats;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotCutRequest;
import bg.deck.belot.repository.BelotPlayerStatsRepository;
import bg.deck.belot.service.BelotDealService;
import bg.deck.belot.service.BelotMatchmakingService;
import bg.deck.belot.service.BelotPlayService;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotService;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.belot.service.BelotTableService;
import bg.deck.belot.service.BelotTurnService;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.service.WebSocketService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Giving up.
 *
 * <p>Belot is scored per pair and the sheet has two columns, so a game cannot
 * end for two of the four and go on for the other two: a concession ends it
 * for the partner too. Their record does not take the loss, though — it was
 * not theirs to give. Whoever conceded loses twice the rating, still one loss,
 * as when a player lets their time run out three times. That is the decision
 * this pins down, along with the two things that have to stop when it happens
 * — the hand in progress and the turn clock over it.
 */
@DisplayName("Giving up a game")
@DataJpaTest
@EnableConfigurationProperties(BelotProperties.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotService.class, BelotTableService.class, BelotMatchmakingService.class, BelotDealService.class, BelotPlayService.class,
        BelotTurnService.class, BelotPlayerService.class, BelotSeedService.class, BelotStatsService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotsurrender;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotSurrenderTest {

    /** North, East, South, West, in that order: petko and gosho are partners. */
    private static final List<String> PLAYERS = List.of("petko91", "ninja2011", "gosho", "ivan");

    @MockitoBean private AvailabilityService availabilityService;
    @MockitoBean private WebSocketService webSocketService;

    @Autowired private BelotService belot;
    @Autowired private BelotTableService tables;
    @Autowired private BelotDealService deals;
    @Autowired private BelotPlayerStatsRepository records;
    @Autowired private EntityManager entityManager;

    private BelotGame seatFour() {
        PLAYERS.forEach(belot::search);
        BelotGame table = tables.tableOf(PLAYERS.getFirst()).orElseThrow();
        cut(table);
        return table;
    }

    /** The player on the dealer's left cuts, which opens the bidding. */
    private void cut(BelotGame table) {
        BelotDeal dealt = deals.current(table).orElseThrow();
        String cutter = table.getSeats().stream()
                .filter(taken -> taken.getSeat() == dealt.cutter())
                .findFirst()
                .orElseThrow()
                .getUsername();
        belot.cut(cutter, new BelotCutRequest(16));
    }

    @Test
    @DisplayName("the game goes to the other pair, and the partner is given the win")
    void theGameGoesToTheOtherPair() {
        BelotGame table = seatFour();

        belot.surrender("petko91");

        assertEquals(BelotGameStatus.FINISHED, table.getStatus());
        assertEquals(Team.EAST_WEST, table.getWinnerTeam(),
                "petko sits north, so the game goes to east and west");

        entityManager.flush();
        entityManager.clear();

        BelotPlayerStats partner = records.findByUsername("gosho").orElseThrow();
        assertEquals(1, partner.getWins(), "his partner did not give it up: it is a win for him");
        assertEquals(0, partner.getLosses());
        assertEquals(1, records.findByUsername("ninja2011").orElseThrow().getWins());
        assertEquals(1, records.findByUsername("ivan").orElseThrow().getWins());
    }

    @Test
    @DisplayName("whoever concedes loses twice the rating, and it is still one loss")
    void theOneWhoConcedesLosesDouble() {
        seatFour();

        belot.surrender("petko91");

        entityManager.flush();
        entityManager.clear();

        // Four new players, all on the starting rating: an ordinary result is
        // the same size either way, so the concession is exactly twice a win.
        BelotPlayerStats conceded = records.findByUsername("petko91").orElseThrow();
        int ordinary = records.findByUsername("ninja2011").orElseThrow().getRating() - BelotPlayerStats.STARTING_RATING;
        assertEquals(2 * ordinary, BelotPlayerStats.STARTING_RATING - conceded.getRating(),
                "the rating falls twice as far as an ordinary result moves it");
        assertEquals(1, conceded.getLosses(), "one game, one loss on the record");
        assertEquals(0, conceded.getWins());
    }

    @Test
    @DisplayName("the hand in progress is given up on, not counted")
    void theHandIsAbandoned() {
        BelotGame table = seatFour();

        belot.surrender("petko91");

        BelotDeal deal = deals.current(table).orElseThrow();
        assertEquals(BelotDealStatus.ABANDONED, deal.getStatus());
        assertEquals(0, table.getNorthSouthScore() + table.getEastWestScore() + table.getHangingPoints(),
                "nothing went on the sheet for a hand nobody finished");
    }

    @Test
    @DisplayName("and the turn clock lets the table alone afterwards")
    void theClockStops() {
        BelotGame table = seatFour();
        BelotDeal deal = deals.current(table).orElseThrow();

        assertTrue(belot.clockOf(deal.getId()).isPresent(), "the clock is running before the concession");

        belot.surrender("petko91");

        assertTrue(belot.clockOf(deal.getId()).isEmpty(),
                "a hand given up on is not a hand somebody is late for");
        assertTrue(deals.inProgress().isEmpty(), "nor one the timers are set again for after a restart");
    }

    @Test
    @DisplayName("pressing it twice changes nothing")
    void surrenderingTwiceIsHarmless() {
        BelotGame table = seatFour();

        belot.surrender("petko91");
        belot.surrender("gosho");

        assertEquals(Team.EAST_WEST, table.getWinnerTeam(), "the first one settled it");

        entityManager.flush();
        entityManager.clear();

        assertEquals(1, records.findByUsername("gosho").orElseThrow().getGames(),
                "and it is one game on the record, not two: the second press found"
                        + " no live table and did nothing");
    }

    @Test
    @DisplayName("a score already on the sheet stands: a conceded game is not invented as a 151")
    void theScoreStandsAsItWas() {
        BelotGame table = seatFour();
        table.setNorthSouthScore(40);
        table.setEastWestScore(12);
        tables.save(table);

        belot.surrender("ninja2011");

        assertEquals(40, table.getNorthSouthScore());
        assertEquals(12, table.getEastWestScore());
        assertEquals(Team.NORTH_SOUTH, table.getWinnerTeam(),
                "the pair that was behind gave up, and the sheet still says they were ahead");
    }

    @Test
    @DisplayName("it works during the bidding too, not only once cards are down")
    void givingUpWhileBidding() {
        BelotGame table = seatFour();
        BelotDeal deal = deals.current(table).orElseThrow();
        assertEquals(BelotDealStatus.BIDDING, deal.getStatus());

        Seat toAct = deal.bidding().toAct();
        String speaker = table.getSeats().stream()
                .filter(seat -> seat.getSeat() == toAct)
                .findFirst().orElseThrow().getUsername();
        belot.bid(speaker, new BelotBidRequest(BidKind.BID, Contract.SPADES));

        belot.surrender(speaker);

        assertEquals(BelotGameStatus.FINISHED, table.getStatus());
        assertEquals(BelotDealStatus.ABANDONED, deals.current(table).orElseThrow().getStatus());
    }
}
