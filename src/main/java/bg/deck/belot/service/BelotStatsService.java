package bg.deck.belot.service;

import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.belot.engine.TeamElo;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotPlayerStats;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotPlayerStatsRepository;
import bg.deck.constant.Constants;
import bg.deck.util.RankLadder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What belot remembers about a player: the only place
 * {@link BelotPlayerStatsRepository} is spoken to.
 *
 * <p>A record is written when a game ends, for all four at once — belot is a
 * partnership game, and a win belongs to a pair rather than to whoever played
 * the last card. The rating moves the same way: see {@link TeamElo} for why
 * both partners take the same result.
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
     * says so. A seat whose account has since been deleted is skipped —
     * there is nobody left to hold the result.
     */
    @Transactional
    public void record(BelotGame game) {
        Team winner = game.getWinnerTeam();
        if (winner == null) {
            return;
        }

        // Every rating is read before any is written: a pair is rated on what
        // the two of them brought to the table, not on what the first of them
        // has already been given for this same game.
        Map<Seat, BelotPlayerStats> before = new EnumMap<>(Seat.class);
        for (BelotSeat seat : game.getSeats()) {
            // A seat left by a deleted account carries a name nobody owns, and
            // two of them at one table would be one row written twice.
            if (Constants.DELETED_PLAYER.equals(seat.getUsername())) {
                continue;
            }
            before.put(seat.getSeat(), belotPlayerStatsRepository.findByUsername(seat.getUsername())
                    .orElseGet(() -> new BelotPlayerStats(seat.getUsername())));
        }

        double northSouth = ratingOf(before, Team.NORTH_SOUTH);
        double eastWest = ratingOf(before, Team.EAST_WEST);

        for (BelotSeat seat : game.getSeats()) {
            BelotPlayerStats stats = before.get(seat.getSeat());
            if (stats == null) {
                continue;
            }
            boolean won = seat.team() == winner;
            boolean sitsNorthSouth = seat.team() == Team.NORTH_SOUTH;

            int delta = TeamElo.delta(
                    sitsNorthSouth ? northSouth : eastWest,
                    sitsNorthSouth ? eastWest : northSouth,
                    won,
                    RankLadder.kFactor(stats.getGames()));

            stats.record(won, delta);
            belotPlayerStatsRepository.save(stats);

            log.info("Belot: {} {} — rating {} ({}{}), rank {}", stats.getUsername(),
                    won ? "won" : "lost", stats.getRating(), delta >= 0 ? "+" : "", delta,
                    stats.getRank());
        }

        log.info("Belot: table {} is over, {} won", game.getId(), winner);
    }

    /** A pair as one number: the two of them, evenly. */
    private static double ratingOf(Map<Seat, BelotPlayerStats> stats, Team team) {
        List<BelotPlayerStats> pair = stats.entrySet().stream()
                .filter(seated -> Team.of(seated.getKey()) == team)
                .map(Map.Entry::getValue)
                .toList();

        if (pair.isEmpty()) {
            return BelotPlayerStats.STARTING_RATING;
        }

        return TeamElo.teamRating(pair.getFirst().getRating(), pair.getLast().getRating());
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
