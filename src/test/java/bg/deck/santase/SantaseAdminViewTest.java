package bg.deck.santase;

import bg.deck.common.model.dto.AdminGameRow;
import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseSeat;
import bg.deck.santase.service.SantaseAdminView;
import bg.deck.santase.service.SantaseStatsService;
import bg.deck.santase.service.SantaseTableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Сантасе as the admin page sees a player's last games. */
@DisplayName("Santase, for the admin page")
class SantaseAdminViewTest {

    private final SantaseTableService tables = mock(SantaseTableService.class);
    private final SantaseAdminView view = new SantaseAdminView(mock(SantaseStatsService.class), tables);

    @Test
    @DisplayName("names the opponent, and says who won and who gave it up")
    void aRowPerGame() {
        SantaseGame won = game("petko91", "ninja2011");
        won.setWinner(won.getFirstPlayer(), false);
        SantaseGame given = game("gosho", "petko91");
        given.setWinner(given.getFirstPlayer(), true);
        when(tables.finishedGamesOf("petko91", 10)).thenReturn(List.of(won, given));

        List<AdminGameRow> rows = view.recentGames("petko91", 10);

        assertThat(rows.get(0).others()).containsExactly("ninja2011");
        assertThat(rows.get(0).won()).isTrue();
        assertThat(rows.get(0).surrendered()).isFalse();
        assertThat(rows.get(1).others()).containsExactly("gosho");
        assertThat(rows.get(1).won()).isFalse();
        assertThat(rows.get(1).surrendered()).isTrue();
    }

    private static SantaseGame game(String first, String second) {
        return SantaseGame.builder().firstPlayer(seat(first)).secondPlayer(seat(second)).build();
    }

    private static SantaseSeat seat(String username) {
        SantaseSeat seat = SantaseSeat.builder().username(username).build();
        // Entities compare by id, and two seats must not be equal.
        try {
            var field = Class.forName("bg.deck.common.model.base.BaseEntity").getDeclaredField("id");
            field.setAccessible(true);
            field.set(seat, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return seat;
    }
}
