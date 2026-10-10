package bg.deck.tabla;

import bg.deck.common.constant.Constants;
import bg.deck.common.service.UserAccountService;
import bg.deck.tabla.engine.BoardState;
import bg.deck.tabla.model.TablaGame;
import bg.deck.tabla.model.TablaGameState;
import bg.deck.tabla.model.TablaSeat;
import bg.deck.tabla.repository.TablaGameRepository;
import bg.deck.tabla.service.TablaSeatService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Табла keeps to its own schema, checked by the database rather than by
 * intention — as belot's is in {@code BelotSchemaTest}.
 */
@DisplayName("Табла keeps to its own schema")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TablaSeatService.class)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:tablaschema;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class TablaSchemaTest {

    @Autowired private TablaGameRepository games;
    @Autowired private TablaSeatService seats;
    @Autowired private EntityManager entityManager;
    @MockitoBean private UserAccountService userAccountService;

    @Test
    @DisplayName("табла owns exactly its own tables")
    void theTablesLiveInTheirOwnSchema() {
        List<?> tables = entityManager.createNativeQuery("""
                        select lower(table_name) from information_schema.tables
                         where upper(table_schema) = 'TABLA'
                         order by 1
                        """)
                .getResultList();

        assertThat(tables).isEqualTo(List.of("game", "game_state", "player_stats", "seat"));
    }

    @Test
    @DisplayName("nothing табла owns points out of the schema")
    void noForeignKeyLeavesTheSchema() {
        List<?> references = entityManager.createNativeQuery("""
                        select fk.constraint_name
                          from information_schema.referential_constraints fk
                         where upper(fk.constraint_schema) = 'TABLA'
                           and upper(fk.unique_constraint_schema) <> 'TABLA'
                        """)
                .getResultList();

        assertThat(references).isEmpty();
    }

    @Test
    @DisplayName("a game comes back with its seed, its board and its seats")
    void aGameRoundTrips() {
        byte[] seed = {1, 2, 3, 4};
        UUID gameId = games.save(newGame("petko91", "ninja2011", seed)).getId();
        flushAndClear();

        TablaGame reread = games.findById(gameId).orElseThrow();
        assertThat(reread.getServerSeed()).isEqualTo(seed);
        assertThat(reread.getState().getBoard()).isEqualTo(BoardState.initial().encode());
        assertThat(reread.getFirstPlayer().getUsername()).isEqualTo("petko91");
        assertThat(games.findActiveGamesByUsername("ninja2011")).extracting(TablaGame::getId).containsExactly(gameId);
        assertThat(games.findActiveGameIdsByUsername("ninja2011")).containsExactly(gameId);
        assertThat(games.findActiveGameIdsByUsername("nobody")).isEmpty();
    }

    @Test
    @DisplayName("a deleted account's seats keep their games, under the tombstone name")
    void aDeletedAccountsSeatsAreRenamed() {
        UUID gameId = games.save(newGame("petko91", "ninja2011", new byte[]{9})).getId();
        flushAndClear();

        assertThat(seats.anonymise("petko91")).isEqualTo(1);
        flushAndClear();

        TablaGame reread = games.findById(gameId).orElseThrow();
        assertThat(reread.getFirstPlayer().getUsername()).isEqualTo(Constants.DELETED_PLAYER);
        assertThat(reread.getSecondPlayer().getUsername()).isEqualTo("ninja2011");
        assertThat(games.findActiveGamesByUsername("petko91")).isEmpty();
    }

    private TablaGame newGame(String first, String second, byte[] seed) {
        TablaSeat one = TablaSeat.builder().username(first).inactivityCount(0).build();
        TablaSeat two = TablaSeat.builder().username(second).inactivityCount(0).build();
        entityManager.persist(one);
        entityManager.persist(two);
        TablaGameState state = TablaGameState.builder()
                .board(BoardState.initial().encode())
                .turnIndex(0)
                .maxDiceUsable(0)
                .build();
        return TablaGame.builder().firstPlayer(one).secondPlayer(two).state(state)
                .serverSeed(seed).serverSeedHash("hash").build();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
