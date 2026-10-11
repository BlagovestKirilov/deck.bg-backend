package bg.deck.tabla.service;

import bg.deck.common.model.dto.AdminGameRecord;
import bg.deck.common.model.dto.AdminGameRow;
import bg.deck.common.service.GameAdminView;
import bg.deck.tabla.model.TablaGame;
import bg.deck.tabla.model.TablaSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Табла as the admin page sees a player. Listed second. */
@Order(2)
@RequiredArgsConstructor
@Component
public class TablaAdminView implements GameAdminView {

    private final TablaStatsService tablaStatsService;
    private final TablaUtilService tablaUtilService;

    @Override
    public String code() {
        return TablaService.TABLA;
    }

    @Override
    public AdminGameRecord recordOf(String username) {
        return tablaStatsService.adminRecord(username);
    }

    @Override
    public Optional<UUID> activeGameId(String username) {
        return tablaUtilService.activeGameId(username);
    }

    @Override
    public List<AdminGameRow> recentGames(String username, int limit) {
        return tablaUtilService.finishedGamesOf(username, limit).stream()
                .map(game -> rowOf(game, username))
                .toList();
    }

    private static AdminGameRow rowOf(TablaGame game, String username) {
        TablaSeat seat = game.getPlayerByUsername(username);
        TablaSeat opponent = game.getOpponent(seat);
        return new AdminGameRow(
                game.getId(),
                game.getFinishedAt(),
                List.of(opponent.getUsername()),
                seat.equals(game.getWinner()),
                seat.equals(game.getSurrenderPlayer()));
    }
}
