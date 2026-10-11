package bg.deck.belot;

import bg.deck.belot.enums.BelotForfeit;
import bg.deck.belot.enums.BelotGameStatus;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.belot.service.BelotAdminView;
import bg.deck.belot.service.BelotMatchmakingService;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotSeedService;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.belot.service.BelotTableService;
import bg.deck.common.model.dto.AdminGameRow;
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
 * Belot as the admin page sees a player: four seats, so the others are three
 * and a win is the pair's.
 */
@DisplayName("Belot, for the admin page")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BelotAdminView.class, BelotTableService.class, BelotMatchmakingService.class, BelotPlayerService.class,
        BelotSeedService.class, BelotStatsService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:belotadmin;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class BelotAdminViewTest {

    @Autowired private BelotAdminView view;
    @Autowired private BelotGameRepository games;
    @Autowired private EntityManager entityManager;

    /** Seated N, W, S, E in that order: the first and third are partners. */
    private BelotGame table(BelotGameStatus status, Team winner, String... usernames) {
        BelotGame game = new BelotGame();
        game.setServerSeed(new byte[]{1, 2, 3});
        game.setServerSeedHash("hash");
        game.setStatus(status);
        game.setWinnerTeam(winner);
        Seat seat = Seat.NORTH;
        for (String username : usernames) {
            game.add(new BelotSeat(seat, username));
            seat = seat.next();
        }
        return games.save(game);
    }

    @Test
    @DisplayName("finished tables only, won or lost by the pair, with the other three named")
    void finishedTables() {
        UUID won = table(BelotGameStatus.FINISHED, Team.NORTH_SOUTH, "petko91", "ninja", "gosho", "ivan").getId();
        BelotGame given = table(BelotGameStatus.FINISHED, Team.NORTH_SOUTH, "ninja", "petko91", "gosho", "ivan");
        given.setForfeit(BelotForfeit.SURRENDER);
        given.setForfeitedBy("petko91");
        UUID lost = games.save(given).getId();
        UUID live = table(BelotGameStatus.PLAYING, null, "petko91", "a", "b", "c").getId();
        entityManager.flush();
        entityManager.clear();

        List<AdminGameRow> rows = view.recentGames("petko91", 10);

        assertThat(rows).extracting(AdminGameRow::gameId).containsExactlyInAnyOrder(won, lost);
        AdminGameRow wonRow = rows.stream().filter(row -> row.gameId().equals(won)).findFirst().orElseThrow();
        AdminGameRow lostRow = rows.stream().filter(row -> row.gameId().equals(lost)).findFirst().orElseThrow();
        assertThat(wonRow.won()).isTrue();
        assertThat(wonRow.surrendered()).isFalse();
        assertThat(wonRow.others()).containsExactlyInAnyOrder("ninja", "gosho", "ivan");
        assertThat(lostRow.won()).isFalse();
        assertThat(lostRow.surrendered()).isTrue();
        assertThat(view.activeGameId("petko91")).contains(live);
    }

    @Test
    @DisplayName("a player who has not played has a fresh record and nothing to show, and none is saved")
    void aNewcomer() {
        assertThat(view.recordOf("nobody").rating()).isEqualTo(1500);
        assertThat(view.recordOf("nobody").wins()).isZero();
        assertThat(view.recentGames("nobody", 10)).isEmpty();
        assertThat(view.activeGameId("nobody")).isEmpty();
        assertThat(entityManager.createQuery("select count(s) from BelotPlayerStats s", Long.class)
                .getSingleResult()).isZero();
    }
}
