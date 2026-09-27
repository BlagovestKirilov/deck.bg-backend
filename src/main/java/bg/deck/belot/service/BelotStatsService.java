package bg.deck.belot.service;

import bg.deck.belot.engine.Team;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotPlayerStats;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotPlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What belot remembers about a player: the only place
 * {@link BelotPlayerStatsRepository} is spoken to.
 *
 * <p>A record is written when a game ends, for all four at once — belot is a
 * partnership game, and a win belongs to a pair rather than to whoever played
 * the last card.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotStatsService {

    private final BelotPlayerStatsRepository belotPlayerStatsRepository;

    /** The record under this name, started on first sight. */
    @Transactional(readOnly = true)
    public BelotPlayerStats of(String username) {
        return belotPlayerStatsRepository.findByUsername(username)
                .orElseGet(() -> new BelotPlayerStats(username));
    }

    /**
     * Writes the result of a finished game against all four seats.
     *
     * <p>Called once, with the table that has just been won: a player who
     * leaves before the last card still played the game, and their record
     * says so.
     */
    @Transactional
    public void record(BelotGame game) {
        Team winner = game.getWinnerTeam();
        if (winner == null) {
            return;
        }

        for (BelotSeat seat : game.getSeats()) {
            BelotPlayerStats stats = belotPlayerStatsRepository.findByUsername(seat.getUsername())
                    .orElseGet(() -> new BelotPlayerStats(seat.getUsername()));
            stats.record(seat.team() == winner);
            belotPlayerStatsRepository.save(stats);
        }

        log.info("Belot: table {} recorded against four players, {} won", game.getId(), winner);
    }

    /** Forgets a player whose account is gone. */
    @Transactional
    public boolean forget(String username) {
        return belotPlayerStatsRepository.findByUsername(username)
                .map(stats -> {
                    belotPlayerStatsRepository.delete(stats);
                    return true;
                })
                .orElse(false);
    }
}
