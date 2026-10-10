package bg.deck.santase.service;

import bg.deck.common.model.Game;
import bg.deck.common.model.Player;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.util.Elo;
import bg.deck.common.util.RankLadder;
import bg.deck.santase.model.SantasePlayerStats;
import bg.deck.santase.repository.SantasePlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What santase remembers about a player: the only class that speaks to
 * {@link SantasePlayerStatsRepository}.
 *
 * <p>A record is made the first time a player finishes a game, not when they
 * register: until then the profile shows a fresh one. A player is in one
 * santase game at a time, so two results for the same record never arrive at
 * once.
 */
@RequiredArgsConstructor
@Service
public class SantaseStatsService {

    private final SantasePlayerStatsRepository santasePlayerStatsRepository;

    /** This player's record, as the profile shows it. Writes nothing. */
    @Transactional(readOnly = true)
    public GameStatsDTO view(String username) {
        SantasePlayerStats stats = santasePlayerStatsRepository.findByUsername(username)
                .orElseGet(() -> new SantasePlayerStats(username));
        return new GameStatsDTO(stats.getWins(), stats.getLosses(), stats.getRank().name(),
                RankLadder.placementGamesRemaining(stats.totalGames()));
    }

    /**
     * Writes a finished game into both players' records. A seat whose account
     * has since been deleted has no record to write; the other is still
     * settled.
     */
    @Transactional
    public void record(Game game) {
        Player winner = game.getWinner();
        SantasePlayerStats won = recordOf(winner);
        SantasePlayerStats lost = recordOf(game.getOpponent(winner));
        Elo.settle(won, lost);
        if (won != null) {
            santasePlayerStatsRepository.save(won);
        }
        if (lost != null) {
            santasePlayerStatsRepository.save(lost);
        }
    }

    /** Forgets a deleted account's record. */
    @Transactional
    public boolean forget(String username) {
        return santasePlayerStatsRepository.findByUsername(username)
                .map(stats -> {
                    santasePlayerStatsRepository.delete(stats);
                    return true;
                })
                .orElse(false);
    }

    private SantasePlayerStats recordOf(Player seat) {
        if (seat == null || seat.getUser() == null) {
            return null;
        }
        String username = seat.getUser().getUsername();
        return santasePlayerStatsRepository.findByUsername(username)
                .orElseGet(() -> new SantasePlayerStats(username));
    }
}
