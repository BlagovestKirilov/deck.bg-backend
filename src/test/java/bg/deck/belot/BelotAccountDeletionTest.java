package bg.deck.belot;

import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.belot.repository.BelotPlayerRepository;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotTableService;
import bg.deck.constant.Constants;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What belot keeps of a player who deletes their account, and what it lets go.
 *
 * <p>Against a real database, because the point of it is what the rows say
 * afterwards. The account itself is deleted in the public schema; belot only
 * ever hears a username, which is the whole of the seam.
 *
 * <p>The two services are called directly rather than through
 * {@link BelotAccountListener}. That listener runs after the deletion has
 * committed, in a transaction of its own — inside a test transaction that
 * never commits it would be looking at an empty database, and the test would
 * be measuring Spring’s transaction rules rather than belot’s behaviour. What
 * it does is one line per service, and those two lines are what is checked
 * here.
 */
@DisplayName("A deleted account, as belot sees it")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotTableService.class, BelotPlayerService.class, BelotSeedService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotdeletion;INIT=CREATE SCHEMA IF NOT EXISTS belot",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotAccountDeletionTest {

    @Autowired private BelotTableService tables;
    @Autowired private BelotPlayerService players;
    @Autowired private BelotGameRepository games;
    @Autowired private BelotPlayerRepository playerRows;
    @Autowired private EntityManager entityManager;

    private BelotGame aTableWith(String... usernames) {
        BelotGame game = new BelotGame();
        game.setServerSeed(new byte[]{1, 2, 3});
        game.setServerSeedHash("hash");

        Seat seat = Seat.NORTH;
        for (String username : usernames) {
            game.add(new BelotSeat(seat, username));
            players.ensureKnown(username);
            seat = seat.next();
        }
        return games.save(game);
    }

    @Test
    @DisplayName("their seat keeps the game whole, under a name nobody owns")
    void theSeatStaysAndTheNameGoes() {
        BelotGame game = aTableWith("petko91", "ninja2011", "gosho", "ivan");

        int renamed = tables.anonymise("petko91");
        players.forget("petko91");
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, renamed, "they sat in one seat");
        BelotGame after = games.findById(game.getId()).orElseThrow();
        assertEquals(4, after.getSeats().size(),
                "a finished game still has to say four people were at it");

        List<String> names = after.getSeats().stream().map(BelotSeat::getUsername).sorted().toList();
        assertFalse(names.contains("petko91"), "the name is gone");
        assertTrue(names.contains(Constants.DELETED_PLAYER), "and the seat says so");
        assertTrue(names.containsAll(List.of("gosho", "ivan", "ninja2011")),
                "the other three are untouched: their record is not theirs to lose");
    }

    @Test
    @DisplayName("and belot forgets them as a player")
    void thePlayerRowGoes() {
        aTableWith("petko91", "ninja2011", "gosho", "ivan");

        assertTrue(players.forget("petko91"), "there was one to forget");
        entityManager.flush();
        entityManager.clear();

        assertTrue(playerRows.findByUsername("petko91").isEmpty());
        assertTrue(playerRows.findByUsername("gosho").isPresent(), "nobody else is forgotten");
    }

    @Test
    @DisplayName("somebody who never played belot is no trouble")
    void aStrangerIsFine() {
        // An account deleted in the public schema must not fail because belot
        // never met them.
        assertEquals(0, tables.anonymise("nobody-here"));
        assertFalse(players.forget("nobody-here"));
    }
}
