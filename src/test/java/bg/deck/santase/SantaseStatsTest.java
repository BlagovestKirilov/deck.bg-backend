package bg.deck.santase;

import bg.deck.common.model.Game;
import bg.deck.common.model.Player;
import bg.deck.common.model.User;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.util.Elo;
import bg.deck.santase.model.SantasePlayerStats;
import bg.deck.santase.repository.SantasePlayerStatsRepository;
import bg.deck.santase.service.SantaseStatsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Santase's records, in santase's own schema.
 *
 * <p>Kept by username, made on a player's first result rather than at
 * registration, and let go of when the account is deleted.
 */
@DisplayName("Santase records")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SantaseStatsService.class)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:santasestats;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class SantaseStatsTest {

    @Autowired private SantaseStatsService stats;
    @Autowired private SantasePlayerStatsRepository records;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("the table is santase's own, in the santase schema")
    @SuppressWarnings("unchecked")
    void theTableIsInTheSantaseSchema() {
        List<String> tables = entityManager.createNativeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'SANTASE'").getResultList();
        assertThat(tables).contains("PLAYER_STATS");
    }

    @Test
    @DisplayName("a first result makes both records: the win, the loss and the rating moved each way")
    void aFirstResultMakesBothRecords() {
        stats.record(gameWonBy("petko91", "gosho"));
        flush();

        SantasePlayerStats winner = records.findByUsername("petko91").orElseThrow();
        SantasePlayerStats loser = records.findByUsername("gosho").orElseThrow();
        assertThat(winner.getWins()).isEqualTo(1);
        assertThat(winner.getLosses()).isZero();
        assertThat(loser.getLosses()).isEqualTo(1);
        assertThat(winner.getRating()).isEqualTo(1500 + Elo.delta(1500, 1500, true, 1));
        assertThat(loser.getRating()).isEqualTo(1500 + Elo.delta(1500, 1500, false, 1));
    }

    @Test
    @DisplayName("a second result adds to the same record")
    void aSecondResultAddsUp() {
        stats.record(gameWonBy("petko91", "gosho"));
        stats.record(gameWonBy("gosho", "petko91"));
        flush();

        SantasePlayerStats petko = records.findByUsername("petko91").orElseThrow();
        assertThat(petko.getWins()).isEqualTo(1);
        assertThat(petko.getLosses()).isEqualTo(1);
        assertThat(records.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("a deleted account's seat is skipped, and the other player is still settled")
    void aDeletedSeatIsSkipped() {
        Game game = gameWonBy("petko91", "gosho");
        game.getOpponent(game.getWinner()).setUser(null);

        stats.record(game);
        flush();

        assertThat(records.findByUsername("petko91")).isPresent();
        assertThat(records.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the profile reads a record without writing one")
    void viewWritesNothing() {
        GameStatsDTO fresh = stats.view("newcomer");

        assertThat(fresh.wins()).isZero();
        assertThat(fresh.losses()).isZero();
        assertThat(fresh.rank()).isEqualTo("UNRANKED");
        assertThat(records.count()).isZero();
    }

    @Test
    @DisplayName("a deleted account's record is let go of")
    void aDeletedAccountIsForgotten() {
        stats.record(gameWonBy("petko91", "gosho"));
        flush();

        stats.forget("petko91");
        flush();

        assertThat(records.findByUsername("petko91")).isEmpty();
        assertThat(records.findByUsername("gosho")).isPresent();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    /** A finished game between two accounts, never saved: only the result is written. */
    static Game gameWonBy(String winner, String loser) {
        Player won = seat(winner);
        Player lost = seat(loser);
        Game game = Game.builder().firstPlayer(won).secondPlayer(lost).build();
        game.setWinner(won, false);
        return game;
    }

    private static Player seat(String username) {
        User user = new User();
        user.setUsername(username);
        Player player = Player.builder().user(user).build();
        // Entities compare by id, and two seats must not be equal.
        setId(player, UUID.randomUUID());
        return player;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = Class.forName("bg.deck.common.model.base.BaseEntity").getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
