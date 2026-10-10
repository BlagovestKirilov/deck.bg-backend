package bg.deck.belot;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Deck;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.enums.BelotGameStatus;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.belot.service.BelotMatchmakingService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotTableService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sitting four people down at a table, against a real database.
 */
@DisplayName("A belot table")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotTableService.class, BelotMatchmakingService.class, BelotSeedService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belottable;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotTableTest {

    @Autowired private BelotTableService tables;
    @Autowired private BelotGameRepository games;
    @Autowired private EntityManager entityManager;
    @Autowired private BelotSeedService seeds;

    private BelotGame seatFour(String... usernames) {
        BelotGame table = null;
        for (String username : usernames) {
            table = tables.join(username);
        }
        entityManager.flush();
        return table;
    }

    @Nested
    @DisplayName("filling up")
    class FillingUp {

        @Test
        @DisplayName("the first to arrive opens a table and waits")
        void theFirstOneWaits() {
            BelotGame table = tables.join("petko91");

            assertEquals(BelotGameStatus.WAITING, table.getStatus());
            assertEquals(1, table.getSeats().size());
            assertEquals(Seat.NORTH, table.getSeats().getFirst().getSeat());
        }

        @Test
        @DisplayName("the next three join the same one")
        void theOthersJoinIt() {
            BelotGame table = seatFour("petko91", "ninja2011", "gosho", "ivan");

            assertEquals(1, games.count(), "one table, not four");
            assertEquals(4, table.getSeats().size());
        }

        @Test
        @DisplayName("the fourth starts the game")
        void theFourthStartsIt() {
            BelotGame table = seatFour("petko91", "ninja2011", "gosho", "ivan");

            assertEquals(BelotGameStatus.PLAYING, table.getStatus());
            assertTrue(table.isFull());
            assertNotEquals(null, table.getDealerSeat(), "somebody has to deal");
        }

        @Test
        @DisplayName("a fifth player opens a table of their own")
        void theFifthStartsAnother() {
            seatFour("petko91", "ninja2011", "gosho", "ivan");
            BelotGame second = tables.join("maria");

            assertEquals(2, games.count());
            assertEquals(BelotGameStatus.WAITING, second.getStatus());
            assertEquals(1, second.getSeats().size());
        }
    }

    @Nested
    @DisplayName("seating")
    class Seating {

        @Test
        @DisplayName("seats are handed out in playing order")
        void inPlayingOrder() {
            BelotGame table = seatFour("petko91", "ninja2011", "gosho", "ivan");

            assertEquals(List.of(Seat.NORTH, Seat.WEST, Seat.SOUTH, Seat.EAST),
                    table.getSeats().stream().map(seat -> seat.getSeat()).toList());
        }

        @Test
        @DisplayName("so the first and third arrivals are partners")
        void firstAndThirdArePartners() {
            BelotGame table = seatFour("petko91", "ninja2011", "gosho", "ivan");

            assertEquals(Team.NORTH_SOUTH, table.seatOf("petko91").orElseThrow().team());
            assertEquals(Team.NORTH_SOUTH, table.seatOf("gosho").orElseThrow().team());
            assertEquals(Team.EAST_WEST, table.seatOf("ninja2011").orElseThrow().team());
            assertEquals(Team.EAST_WEST, table.seatOf("ivan").orElseThrow().team());
        }
    }

    @Nested
    @DisplayName("coming back")
    class ComingBack {

        @Test
        @DisplayName("a player already at a table is given that table, not another seat")
        void rejoiningFindsTheSameTable() {
            BelotGame first = tables.join("petko91");
            BelotGame again = tables.join("petko91");

            assertEquals(first.getId(), again.getId());
            assertEquals(1, again.getSeats().size(), "they are not seated twice");
            assertEquals(1, games.count());
        }

        @Test
        @DisplayName("and mid-game too")
        void rejoiningMidGame() {
            BelotGame table = seatFour("petko91", "ninja2011", "gosho", "ivan");
            entityManager.clear();

            BelotGame again = tables.join("gosho");

            assertEquals(table.getId(), again.getId());
            assertEquals(BelotGameStatus.PLAYING, again.getStatus());
        }
    }

    @Nested
    @DisplayName("the shuffle it promises")
    class Shuffles {

        @Test
        @DisplayName("a table commits to a seed before anyone sits down")
        void committedUpFront() {
            BelotGame table = tables.join("petko91");

            assertEquals(64, table.getServerSeedHash().length(), "a SHA-256, in hex");
            assertEquals(table.getServerSeedHash(), seeds.hash(table.getServerSeed()),
                    "and it is the hash of the seed the deals come from");
        }

        @Test
        @DisplayName("the same deal number always shuffles the same way")
        void reproducible() {
            byte[] seed = seeds.newSeed();

            assertEquals(Dealing.shuffled(seeds.shuffleFor(seed, 3)),
                    Dealing.shuffled(seeds.shuffleFor(seed, 3)),
                    "a deal can be dealt again from the seed, which is the whole promise");
        }

        @Test
        @DisplayName("and a different deal number does not")
        void differentPerDeal() {
            byte[] seed = seeds.newSeed();

            assertNotEquals(Dealing.shuffled(seeds.shuffleFor(seed, 1)),
                    Dealing.shuffled(seeds.shuffleFor(seed, 2)));
        }

        @Test
        @DisplayName("a deal is a whole deck, shuffled and not invented")
        void everyCardExactlyOnce() {
            List<Card> dealt = Dealing.shuffled(seeds.shuffleFor(seeds.newSeed(), 1));

            assertEquals(Deck.full().size(), dealt.size());
            assertEquals(Set.copyOf(Deck.full()), Set.copyOf(dealt), "the same 32 cards, in another order");
        }

        @Test
        @DisplayName("and no seat is dealt the same hand twice over a run of deals")
        void shufflesDiffer() {
            byte[] seed = seeds.newSeed();

            Set<List<Card>> seen = new java.util.HashSet<>();
            for (int deal = 1; deal <= 50; deal++) {
                seen.add(Dealing.shuffled(seeds.shuffleFor(seed, deal)));
            }

            assertEquals(50, seen.size(), "fifty deal numbers, fifty different shuffles");
        }

        @Test
        @DisplayName("two tables do not share a seed")
        void seedsDiffer() {
            String first = seatFour("petko91", "ninja2011", "gosho", "ivan").getServerSeedHash();
            String second = tables.join("maria").getServerSeedHash();

            assertNotEquals(first, second, "each table commits to a secret of its own");
        }
    }

    @Test
    @DisplayName("everything it stores is in the belot schema")
    void nothingLeaksIntoPublic() {
        seatFour("petko91", "ninja2011", "gosho", "ivan");

        List<?> tablesInBelot = entityManager.createNativeQuery("""
                        select lower(table_name) from information_schema.tables
                         where upper(table_schema) = 'BELOT'
                         order by 1
                        """)
                .getResultList();

        assertEquals(List.of("bid", "deal", "game", "matchmaking", "play", "player", "player_stats", "seat"), tablesInBelot);
    }

    @Test
    @DisplayName("and no foreign key leaves it")
    void noForeignKeyLeavesTheSchema() {
        List<?> outward = entityManager.createNativeQuery("""
                        select fk.constraint_name
                          from information_schema.referential_constraints fk
                         where upper(fk.constraint_schema) = 'BELOT'
                           and upper(fk.unique_constraint_schema) <> 'BELOT'
                        """)
                .getResultList();

        assertEquals(List.of(), outward, "seat points at game, and at nothing outside belot");
    }

    @Test
    @DisplayName("the seat's own team comes from where it sits")
    void seatKnowsItsTeam() {
        BelotGame table = seatFour("petko91", "ninja2011", "gosho", "ivan");

        assertSame(Team.NORTH_SOUTH, table.seatOf("petko91").orElseThrow().team());
    }
}
