package bg.deck.belot;

import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.enums.BelotGameStatus;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Getting up from a table before it starts.
 *
 * <p>Going back to the games, or closing the page, while a table is still
 * short of four gives the seat up, as a santase or табла search is cancelled.
 * A table already being played is not left this way: that is a surrender.
 */
@DisplayName("Leaving a table that has not started")
@DataJpaTest
@EnableConfigurationProperties(BelotProperties.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotService.class, BelotTableService.class, BelotMatchmakingService.class, BelotDealService.class, BelotPlayService.class,
        BelotTurnService.class, BelotPlayerService.class, BelotSeedService.class, BelotStatsService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotleave;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotLeaveTest {

    private static final List<String> PLAYERS = List.of("petko91", "ninja2011", "gosho", "ivan");

    @MockitoBean private AvailabilityService availabilityService;
    @MockitoBean private WebSocketService webSocketService;

    @Autowired private BelotService belot;
    @Autowired private BelotTableService tables;

    @Test
    @DisplayName("frees the seat, and the others stay at the table")
    void theSeatIsFreed() {
        belot.search("petko91");
        belot.search("ninja2011");

        belot.leave("petko91");

        assertTrue(tables.tableOf("petko91").isEmpty(), "back at the games, at no table");
        BelotGame stayed = tables.tableOf("ninja2011").orElseThrow();
        assertEquals(1, stayed.getSeats().size());
        assertEquals(BelotGameStatus.WAITING, stayed.getStatus());
    }

    @Test
    @DisplayName("the last one up takes the table with them, and the next search opens a new one")
    void anEmptyTableIsNotKept() {
        belot.search("petko91");
        BelotGame left = tables.tableOf("petko91").orElseThrow();

        belot.leave("petko91");
        belot.search("ninja2011");

        BelotGame next = tables.tableOf("ninja2011").orElseThrow();
        assertEquals(1, next.getSeats().size(), "nobody is left sitting at the old one");
        assertTrue(tables.find(left.getId()).isEmpty(), "a table nobody sits at is not kept");
    }

    @Test
    @DisplayName("a table already being played is not left: that is a surrender")
    void aGameInProgressIsNotLeft() {
        PLAYERS.forEach(belot::search);

        belot.leave("petko91");

        BelotGame table = tables.tableOf("petko91").orElseThrow();
        assertEquals(BelotGameStatus.PLAYING, table.getStatus());
        assertEquals(4, table.getSeats().size());
    }

    @Test
    @DisplayName("a player at no table leaves nothing, and is not an error")
    void nobodyToGetUp() {
        assertDoesNotThrow(() -> belot.leave("passer-by"));
    }
}
