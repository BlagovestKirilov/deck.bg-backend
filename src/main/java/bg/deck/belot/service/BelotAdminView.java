package bg.deck.belot.service;

import bg.deck.belot.enums.Team;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotPlayerStats;
import bg.deck.belot.model.BelotSeat;
import bg.deck.common.model.dto.AdminGameRecord;
import bg.deck.common.model.dto.AdminGameRow;
import bg.deck.common.service.GameAdminView;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Белот as the admin page sees a player. Listed third.
 *
 * <p>A table has four seats, so a game's others are the partner and both
 * opponents, and winning is the player's pair winning.
 */
@Order(3)
@RequiredArgsConstructor
@Component
public class BelotAdminView implements GameAdminView {

    private final BelotStatsService belotStatsService;
    private final BelotTableService belotTableService;

    @Override
    public String code() {
        return BelotService.BELOT;
    }

    @Override
    public AdminGameRecord recordOf(String username) {
        BelotPlayerStats stats = belotStatsService.of(username);
        return new AdminGameRecord(stats.getWins(), stats.getLosses(), stats.getRating(), stats.getRank().name());
    }

    @Override
    public Optional<UUID> activeGameId(String username) {
        return belotTableService.tableOf(username).map(BelotGame::getId);
    }

    // The seats are read here, so the table's session has to still be open.
    @Override
    @Transactional(readOnly = true)
    public List<AdminGameRow> recentGames(String username, int limit) {
        return belotTableService.finishedTablesOf(username, limit).stream()
                .map(table -> rowOf(table, username))
                .toList();
    }

    private static AdminGameRow rowOf(BelotGame table, String username) {
        List<String> others = table.getSeats().stream()
                .map(BelotSeat::getUsername)
                .filter(name -> !name.equals(username))
                .toList();
        boolean won = table.seatOf(username)
                .map(seat -> Team.of(seat.getSeat()) == table.getWinnerTeam())
                .orElse(false);
        return new AdminGameRow(
                table.getId(),
                table.getUpdatedAt(),
                others,
                won,
                username.equals(table.getForfeitedBy()));
    }
}
