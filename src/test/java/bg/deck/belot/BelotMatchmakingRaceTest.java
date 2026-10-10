package bg.deck.belot;

import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.belot.service.BelotMatchmakingService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Four people press Търси at the same moment.
 *
 * <p>The one test in the belot suite that runs more than one thread, because
 * the bug it guards cannot be reproduced any other way. Sitting a player down
 * is read-then-write — look for a table with room, open one if there is none —
 * and four callers that all read before any of them writes all read "no table
 * with room". The first time four people played belot, they got three tables.
 *
 * <p>Not a {@code @DataJpaTest}: that wraps the test in one transaction which
 * is never committed, so a second thread would see none of the first thread's
 * work and the race could not happen at all. Each thread here needs a
 * transaction of its own that really commits, which is the whole point of the
 * lock living in the database.
 */
@DisplayName("Four players sitting down at once")
@SpringBootTest(classes = BelotMatchmakingRaceTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotrace;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotMatchmakingRaceTest {

    /**
     * Only what seating needs. The whole application would drag in the
     * schedulers, the mail sender and the security chain for a test about one
     * method.
     */
    @EnableAutoConfiguration
    @EntityScan("bg.deck.belot.model")
    @EnableJpaRepositories("bg.deck.belot.repository")
    @Import({BelotTableService.class, BelotMatchmakingService.class, BelotSeedService.class})
    static class Config {
    }

    private static final int SEATS = 4;

    @Autowired private BelotTableService tables;
    @Autowired private BelotGameRepository games;
    @Autowired private TransactionTemplate transaction;

    /**
     * The threads commit, so nothing rolls back between tests the way it does
     * everywhere else in this suite. Tables left by the last test would be
     * tables this one seats people at.
     */
    @BeforeEach
    void anEmptyRoom() {
        transaction.executeWithoutResult(status -> games.deleteAll());
    }

    /**
     * Everybody waits, then everybody goes: not merely quick, simultaneous.
     *
     * <p>Every future is read afterwards. A thread that throws inside an
     * executor is silent otherwise, and a test that counts tables would read
     * the silence as success.
     */
    private void allAtOnce(List<String> players) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<UUID>> seated;

        try (ExecutorService threads = Executors.newFixedThreadPool(players.size())) {
            seated = players.stream()
                    .map(player -> threads.submit(() -> {
                        go.await();
                        return tables.join(player).getId();
                    }))
                    .toList();

            go.countDown();
        }

        for (Future<UUID> future : seated) {
            future.get(20, TimeUnit.SECONDS);
        }
    }

    /** Seats are lazy, and the threads that loaded them are gone. */
    private List<BelotGame> tablesWithSeats() {
        return transaction.execute(status -> {
            List<BelotGame> all = games.findAll();
            all.forEach(game -> game.getSeats().size());
            return all;
        });
    }

    @Test
    @DisplayName("all four land at one table, not at three")
    void fourAtOnce() throws Exception {
        List<String> players = List.of("petko91", "ninja2011", "gosho", "ivan");

        allAtOnce(players);

        List<BelotGame> all = tablesWithSeats();
        assertEquals(1, all.size(), "one table, not one per player: " + all.size() + " were opened");

        BelotGame table = all.getFirst();
        assertEquals(SEATS, table.getSeats().size(), "and all four are sitting at it");
        assertEquals(BelotGameStatus.PLAYING, table.getStatus(), "so the hand starts");
        assertEquals(players.size(), table.getSeats().stream()
                .map(BelotSeat::getUsername).distinct().count(), "each of them once");
        assertEquals(SEATS, table.getSeats().stream()
                        .map(BelotSeat::getSeat).distinct().count(),
                "and in four different seats, so nobody was seated over");
    }

    @Test
    @DisplayName("eight of them fill two tables exactly, with nobody left half-seated")
    void eightFillTwoTables() throws Exception {
        List<String> players = IntStream.rangeClosed(1, 8).mapToObj(i -> "player" + i).toList();

        allAtOnce(players);

        List<BelotGame> all = tablesWithSeats();
        assertEquals(2, all.size(), "two tables for eight players, not one each");
        all.forEach(table -> assertEquals(SEATS, table.getSeats().size(), "both of them full"));
    }

    @Test
    @DisplayName("and one player pressing it four times still takes one seat")
    void oneSeatPerPlayer() throws Exception {
        allAtOnce(List.of("petko91", "petko91", "petko91", "petko91"));

        List<BelotGame> all = tablesWithSeats();
        assertEquals(1, all.size(), "one table");
        assertEquals(1, all.getFirst().getSeats().size(),
                "and one seat: searching again is being put back where you were");
    }
}
