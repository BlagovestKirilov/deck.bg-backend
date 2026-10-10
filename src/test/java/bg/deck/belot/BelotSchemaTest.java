package bg.deck.belot;

import bg.deck.belot.model.BelotPlayer;
import bg.deck.belot.repository.BelotPlayerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam, checked by the database rather than by intention.
 *
 * <p>Belot's tables live in their own schema and refer to nothing in public.
 * That is what makes lifting belot into a service of its own a move rather than
 * an excavation, and it is the kind of rule that decays quietly — one
 * {@code @ManyToOne User} added in a hurry and the schema is welded shut. So it
 * is asserted here, where a mistake fails the build.
 */
@DisplayName("Belot keeps to its own schema")
@DataJpaTest
// The real URL, not a replaced one: the schema is created by its INIT clause,
// and a replaced datasource would quietly drop it — which is exactly how this
// test first passed while proving nothing.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belot;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotSchemaTest {

    @Autowired private BelotPlayerRepository belotPlayerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("the player table is created in belot, not in public")
    void theTableLivesInItsOwnSchema() {
        List<?> belotTables = entityManager.createNativeQuery("""
                        select lower(table_name) from information_schema.tables
                         where upper(table_schema) = 'BELOT'
                         order by 1
                        """)
                .getResultList();

        assertEquals(List.of("bid", "deal", "game", "matchmaking", "play", "player", "player_stats", "seat"), belotTables,
                "belot owns exactly its own tables — this list grows with each migration, "
                        + "and a table appearing anywhere else fails here");
    }

    @Test
    @DisplayName("a player is stored and read back by the name on their token")
    void storedAndFoundByUsername() {
        belotPlayerRepository.save(new BelotPlayer("petko91"));
        entityManager.flush();
        entityManager.clear();

        assertTrue(belotPlayerRepository.findByUsername("petko91").isPresent());
        assertTrue(belotPlayerRepository.findByUsername("ninja2011").isEmpty());
    }

    @Test
    @DisplayName("nothing belot owns points at a user")
    void noForeignKeyToTheUserTable() {
        // The rule is in docs/belot/BUILD.md: belot stores the name, never a
        // reference. A join added later would pass every other test in the
        // suite and only show up as a schema that cannot be moved.
        List<?> references = entityManager.createNativeQuery("""
                        select fk.constraint_name
                          from information_schema.referential_constraints fk
                         where upper(fk.constraint_schema) = 'BELOT'
                           and upper(fk.unique_constraint_schema) <> 'BELOT'
                        """)
                .getResultList();

        assertEquals(List.of(), references,
                "a seat points at its game, and nothing belot owns points out of the schema");
    }
}
