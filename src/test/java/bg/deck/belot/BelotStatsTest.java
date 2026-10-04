package bg.deck.belot;

import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotPlayerStats;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.belot.repository.BelotPlayerStatsRepository;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.common.constant.Constants;
import bg.deck.common.constant.RankingConstants;
import bg.deck.common.enums.Rank;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a finished game writes against the four players, in a real database.
 *
 * <p>{@link BelotRatingTest} has the arithmetic on its own; this is about the
 * rows: four written at once, every rating read before any is written, and a
 * seat whose account is gone left alone.
 */
@DisplayName("A finished game, written against four records")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BelotStatsService.class)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotstats;INIT=CREATE SCHEMA IF NOT EXISTS belot",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotStatsTest {

    @Autowired private BelotStatsService stats;
    @Autowired private BelotGameRepository games;
    @Autowired private BelotPlayerStatsRepository records;
    @Autowired private EntityManager entityManager;

    /** North/South win. The four sit N, E, S, W, so the partners are 1st and 3rd. */
    private BelotGame aFinishedGame(String... usernames) {
        BelotGame game = new BelotGame();
        game.setServerSeed(new byte[]{1, 2, 3});
        game.setServerSeedHash("hash");
        game.setWinnerTeam(Team.NORTH_SOUTH);

        Seat seat = Seat.NORTH;
        for (String username : usernames) {
            game.add(new BelotSeat(seat, username));
            seat = seat.next();
        }
        return games.save(game);
    }

    /** Somebody who already has a rating and enough games behind it to be settled. */
    private void seed(String username, int rating, int played) {
        BelotPlayerStats row = new BelotPlayerStats(username);
        row.setRating(rating);
        row.setGames(played);
        row.setWins(played);
        row.setRank(Rank.GOLD);
        records.save(row);
    }

    private BelotPlayerStats reload(String username) {
        return records.findByUsername(username).orElseThrow();
    }

    @Test
    @DisplayName("both partners move by the same amount, however unalike they are")
    void partnersMoveTogether() {
        // 1900 and 1100 average to 1500, which is what the other pair is worth
        // between them, so on paper this is a coin toss.
        seed("strong", 1900, 40);
        seed("weak", 1100, 40);
        seed("even1", 1500, 40);
        seed("even2", 1500, 40);

        stats.record(aFinishedGame("strong", "even1", "weak", "even2"));
        entityManager.flush();
        entityManager.clear();

        int strongGain = reload("strong").getRating() - 1900;
        int weakGain = reload("weak").getRating() - 1100;

        assertEquals(strongGain, weakGain,
                "the win belongs to the pair: nothing says who carried it");
        assertEquals(RankingConstants.K_RANKED / 2, strongGain, "an even table, so half the K factor");

        assertEquals(1500 - strongGain, reload("even1").getRating(), "and the losers give it back");
        assertEquals(1500 - strongGain, reload("even2").getRating());
    }

    @Test
    @DisplayName("a newcomer's own number still moves further than their partner's")
    void placementMovesFurther() {
        seed("settled", 1500, RankingConstants.PLACEMENT_GAMES);
        seed("even1", 1500, 40);
        seed("even2", 1500, 40);
        // "newcomer" has no row at all, which is how everybody starts.

        stats.record(aFinishedGame("settled", "even1", "newcomer", "even2"));
        entityManager.flush();
        entityManager.clear();

        int settled = reload("settled").getRating() - 1500;
        int newcomer = reload("newcomer").getRating() - BelotPlayerStats.STARTING_RATING;

        assertTrue(newcomer > settled,
                "same result, but we know less about the newcomer: " + newcomer + " vs " + settled);
        assertEquals(1, reload("newcomer").getGames(), "and their record starts here");
        assertEquals(Rank.UNRANKED, reload("newcomer").getRank(),
                "unranked until the placement games are done");
    }

    @Test
    @DisplayName("a pair is rated on what they brought, not on what the first of them was just given")
    void ratingsAreReadBeforeAnyIsWritten() {
        seed("north", 1500, 40);
        seed("south", 1500, 40);
        seed("east", 1500, 40);
        seed("west", 1500, 40);

        stats.record(aFinishedGame("north", "east", "south", "west"));
        entityManager.flush();
        entityManager.clear();

        assertEquals(reload("north").getRating(), reload("south").getRating(),
                "the second partner was rated against the same table as the first");
    }

    @Test
    @DisplayName("the win and the loss land on the right four records")
    void winsAndLosses() {
        stats.record(aFinishedGame("north", "east", "south", "west"));
        entityManager.flush();
        entityManager.clear();

        for (String winner : new String[]{"north", "south"}) {
            assertEquals(1, reload(winner).getWins(), winner + " won");
            assertEquals(1, reload(winner).getGames());
        }
        for (String loser : new String[]{"east", "west"}) {
            assertEquals(1, reload(loser).getLosses(), loser + " lost");
            assertEquals(1, reload(loser).getGames());
        }
    }

    @Test
    @DisplayName("a seat left by a deleted account is not given a record")
    void deletedSeatsAreSkipped() {
        // Both of a pair can have deleted their accounts before the table
        // finishes. Two rows under one name nobody owns would break the unique
        // key, and there is nobody left to hold the result anyway.
        stats.record(aFinishedGame(Constants.DELETED_PLAYER, "east", Constants.DELETED_PLAYER, "west"));
        entityManager.flush();
        entityManager.clear();

        assertTrue(records.findByUsername(Constants.DELETED_PLAYER).isEmpty());
        assertEquals(1, reload("east").getLosses(), "the other two still played a game");
        assertEquals(1, reload("west").getLosses());
    }

    @Test
    @DisplayName("an unfinished table writes nothing")
    void noWinnerNoRecord() {
        BelotGame game = new BelotGame();
        game.setServerSeed(new byte[]{1});
        game.setServerSeedHash("hash");
        game.add(new BelotSeat(Seat.NORTH, "north"));
        games.save(game);

        stats.record(game);
        entityManager.flush();

        assertTrue(records.findByUsername("north").isEmpty());
    }
}
