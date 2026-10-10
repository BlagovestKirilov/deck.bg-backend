package bg.deck.tabla.service;

import bg.deck.tabla.model.TablaGame;
import bg.deck.tabla.model.TablaSeat;
import bg.deck.common.constant.Constants;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.util.Elo;
import bg.deck.common.util.RankLadder;
import bg.deck.tabla.model.TablaPlayerStats;
import bg.deck.tabla.repository.TablaPlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What табла remembers about a player: the only class that speaks to
 * {@link TablaPlayerStatsRepository}.
 *
 * <p>A record is made the first time a player finishes a game, not when they
 * register: until then the profile shows a fresh one. A player is in one табла
 * game at a time, so two results for the same record never arrive at once.
 */
@RequiredArgsConstructor
@Service
public class TablaStatsService {

    private final TablaPlayerStatsRepository tablaPlayerStatsRepository;

    /** This player's record, as the profile shows it. Writes nothing. */
    @Transactional(readOnly = true)
    public GameStatsDTO view(String username) {
        TablaPlayerStats stats = tablaPlayerStatsRepository.findByUsername(username)
                .orElseGet(() -> new TablaPlayerStats(username));
        return new GameStatsDTO(stats.getWins(), stats.getLosses(), stats.getRank().name(),
                RankLadder.placementGamesRemaining(stats.totalGames()));
    }

    /**
     * Writes a finished game into both players' records. A seat whose account
     * has since been deleted has no record to write; the other is still
     * settled.
     */
    @Transactional
    public void record(TablaGame game) {
        TablaSeat winner = game.getWinner();
        TablaPlayerStats won = recordOf(winner);
        TablaPlayerStats lost = recordOf(game.getOpponent(winner));
        Elo.settle(won, lost);
        if (won != null) {
            tablaPlayerStatsRepository.save(won);
        }
        if (lost != null) {
            tablaPlayerStatsRepository.save(lost);
        }
    }

    /** Forgets a deleted account's record. */
    @Transactional
    public boolean forget(String username) {
        return tablaPlayerStatsRepository.findByUsername(username)
                .map(stats -> {
                    tablaPlayerStatsRepository.delete(stats);
                    return true;
                })
                .orElse(false);
    }

    private TablaPlayerStats recordOf(TablaSeat seat) {
        if (seat == null || Constants.DELETED_PLAYER.equals(seat.getUsername())) {
            return null;
        }
        String username = seat.getUsername();
        return tablaPlayerStatsRepository.findByUsername(username)
                .orElseGet(() -> new TablaPlayerStats(username));
    }
}
