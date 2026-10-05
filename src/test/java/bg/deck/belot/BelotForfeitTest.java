package bg.deck.belot;

import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotForfeit;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.BelotPlayerStats;
import bg.deck.belot.model.BelotSeat;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Walking away from a table.
 *
 * <p>A player who closes the browser is played for when their time runs out,
 * and every time that happens is counted against them. The third gives the
 * game to the other pair — the same rule the santase and tabla tables keep —
 * so a table nobody is playing at any more ends instead of playing itself for
 * ever. Whoever let it run out loses twice the rating; it is still one loss.
 */
@DisplayName("Walking away from a table")
@DataJpaTest
@EnableConfigurationProperties(BelotProperties.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotService.class, BelotTableService.class, BelotMatchmakingService.class, BelotDealService.class, BelotPlayService.class,
        BelotTurnService.class, BelotPlayerService.class, BelotSeedService.class, BelotStatsService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotforfeit;INIT=CREATE SCHEMA IF NOT EXISTS belot",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotForfeitTest {

    /** North, East, South, West, in that order: petko and gosho are partners. */
    private static final List<String> PLAYERS = List.of("petko91", "ninja2011", "gosho", "ivan");

    @MockitoBean private AvailabilityService availabilityService;
    @MockitoBean private WebSocketService webSocketService;

    @Autowired private BelotService belot;
    @Autowired private BelotTableService tables;
    @Autowired private BelotDealService deals;
    @Autowired private BelotTurnService turns;
    @Autowired private BelotPlayerStatsRepository records;
    @Autowired private EntityManager entityManager;

    private BelotGame seatFour() {
        PLAYERS.forEach(belot::search);
        return tables.tableOf(PLAYERS.getFirst()).orElseThrow();
    }

    /** The player on the dealer's left cuts, which opens the bidding. */
    private void cut(BelotGame table) {
        BelotDeal dealt = deals.current(table).orElseThrow();
        belot.cut(seatAt(table, dealt.cutter()).getUsername(), new BelotCutRequest(16));
    }

    private static BelotSeat seatAt(BelotGame table, Seat seat) {
        return table.getSeats().stream().filter(taken -> taken.getSeat() == seat).findFirst().orElseThrow();
    }

    /** The seat the table is waiting on, with its time already gone. */
    private Seat runOutTheClock(BelotGame table) {
        BelotDeal deal = deals.current(table).orElseThrow();
        deal.setTurnStartedAt(Instant.now().minusSeconds(3600));
        deals.save(deal);
        return turns.toAct(deal).orElseThrow();
    }

    @Test
    @DisplayName("the third time a player's time runs out, the game goes to the other pair")
    void theThirdMissForfeits() {
        BelotGame table = seatFour();
        cut(table);
        Seat absent = runOutTheClock(table);
        tables.missedTurn(table, absent);
        tables.missedTurn(table, absent);

        belot.timeUp(PLAYERS.getFirst());

        assertEquals(BelotGameStatus.FINISHED, table.getStatus());
        assertEquals(seatAt(table, absent).team().opponent(), table.getWinnerTeam());
        assertEquals(BelotForfeit.INACTIVITY, table.getForfeit());
        assertEquals(seatAt(table, absent).getUsername(), table.getForfeitedBy());
        assertEquals(BelotDealStatus.ABANDONED, deals.current(table).orElseThrow().getStatus(),
                "the hand in progress is given up on, as when somebody concedes");
    }

    @Test
    @DisplayName("whoever let it run out loses twice the rating and it is still one loss; their partner is given the win")
    void theLeaverLosesDouble() {
        BelotGame table = seatFour();
        cut(table);
        Seat absent = runOutTheClock(table);
        tables.missedTurn(table, absent);
        tables.missedTurn(table, absent);
        String leaver = seatAt(table, absent).getUsername();
        String partner = seatAt(table, absent.partner()).getUsername();

        belot.timeUp(PLAYERS.getFirst());

        entityManager.flush();
        entityManager.clear();

        String opponent = seatAt(table, absent.next()).getUsername();
        BelotPlayerStats left = records.findByUsername(leaver).orElseThrow();
        BelotPlayerStats stayed = records.findByUsername(partner).orElseThrow();
        BelotPlayerStats against = records.findByUsername(opponent).orElseThrow();

        // Four new players, all on the starting rating: an ordinary result is
        // the same size either way, so the leaver's is exactly twice the win.
        int ordinary = against.getRating() - BelotPlayerStats.STARTING_RATING;
        assertEquals(2 * ordinary, BelotPlayerStats.STARTING_RATING - left.getRating(),
                "the leaver's rating falls twice as far as an ordinary result moves it");
        assertEquals(1, left.getLosses(), "one game, one loss on the record");

        assertEquals(1, stayed.getWins(), "the partner did nothing wrong: it is a win for them");
        assertEquals(0, stayed.getLosses());
        assertEquals(BelotPlayerStats.STARTING_RATING + ordinary, stayed.getRating());

        assertEquals(1, against.getWins(), "for the other pair it is an ordinary win");
    }

    @Test
    @DisplayName("a missed turn short of three is counted, and the game goes on")
    void oneMissIsAWarning() {
        BelotGame table = seatFour();
        cut(table);
        Seat absent = runOutTheClock(table);

        belot.timeUp(PLAYERS.getFirst());

        assertEquals(BelotGameStatus.PLAYING, table.getStatus());
        assertEquals(1, seatAt(table, absent).getMissedTurns());
        assertNull(table.getForfeit());
    }

    @Test
    @DisplayName("a missed cut is not counted: the deck is cut for them and no card changes")
    void aMissedCutIsNotCounted() {
        BelotGame table = seatFour();
        Seat cutter = runOutTheClock(table);

        belot.timeUp(PLAYERS.getFirst());

        assertEquals(0, seatAt(table, cutter).getMissedTurns());
        assertEquals(BelotGameStatus.PLAYING, table.getStatus());
    }
}
