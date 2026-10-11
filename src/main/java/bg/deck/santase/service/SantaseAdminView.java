package bg.deck.santase.service;

import bg.deck.common.model.dto.AdminGameRecord;
import bg.deck.common.model.dto.AdminGameRow;
import bg.deck.common.service.GameAdminView;
import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Сантасе as the admin page sees a player. Listed first. */
@Order(1)
@RequiredArgsConstructor
@Component
public class SantaseAdminView implements GameAdminView {

    private final SantaseStatsService santaseStatsService;
    private final SantaseTableService santaseTableService;

    @Override
    public String code() {
        return SantaseService.SANTASE;
    }

    @Override
    public AdminGameRecord recordOf(String username) {
        return santaseStatsService.adminRecord(username);
    }

    @Override
    public Optional<UUID> activeGameId(String username) {
        return santaseTableService.activeGameId(username);
    }

    @Override
    public List<AdminGameRow> recentGames(String username, int limit) {
        return santaseTableService.finishedGamesOf(username, limit).stream()
                .map(game -> rowOf(game, username))
                .toList();
    }

    private static AdminGameRow rowOf(SantaseGame game, String username) {
        SantaseSeat seat = game.getPlayerByUsername(username);
        SantaseSeat opponent = game.getOpponent(seat);
        return new AdminGameRow(
                game.getId(),
                game.getFinishedAt(),
                List.of(opponent.getUsername()),
                seat.equals(game.getWinner()),
                seat.equals(game.getSurrenderPlayer()));
    }
}
