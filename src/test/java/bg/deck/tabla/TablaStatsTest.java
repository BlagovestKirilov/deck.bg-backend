package bg.deck.tabla;

import bg.deck.tabla.model.TablaGame;
import bg.deck.tabla.model.TablaSeat;
import bg.deck.tabla.model.TablaPlayerStats;
import bg.deck.tabla.repository.TablaPlayerStatsRepository;
import bg.deck.tabla.service.TablaStatsService;
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

/** Табла's records, in tabla's own schema. */
@DisplayName("Табла records")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TablaStatsService.class)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:tablastats;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class TablaStatsTest {

    @Autowired private TablaStatsService stats;
    @Autowired private TablaPlayerStatsRepository records;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("the table is табла's own, in the tabla schema")
    @SuppressWarnings("unchecked")
    void theTableIsInTheTablaSchema() {
        List<String> tables = entityManager.createNativeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'TABLA'").getResultList();
        assertThat(tables).contains("PLAYER_STATS");
    }

    @Test
    @DisplayName("a result is written into both records, and a deleted account's is let go of")
    void aResultAndADeletion() {
        TablaSeat won = seat("petko91");
        TablaSeat lost = seat("gosho");
        TablaGame game = TablaGame.builder().firstPlayer(won).secondPlayer(lost).build();
        game.setWinner(won, true);

        stats.record(game);
        entityManager.flush();
        entityManager.clear();

        TablaPlayerStats winner = records.findByUsername("petko91").orElseThrow();
        assertThat(winner.getWins()).isEqualTo(1);
        assertThat(records.findByUsername("gosho").orElseThrow().getLosses()).isEqualTo(1);

        stats.forget("gosho");
        entityManager.flush();

        assertThat(records.findByUsername("gosho")).isEmpty();
    }

    private static TablaSeat seat(String username) {
        TablaSeat player = TablaSeat.builder().username(username).build();
        try {
            var field = Class.forName("bg.deck.common.model.base.BaseEntity").getDeclaredField("id");
            field.setAccessible(true);
            field.set(player, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return player;
    }
}
