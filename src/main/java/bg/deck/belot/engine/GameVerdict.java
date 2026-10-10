package bg.deck.belot.engine;

import bg.deck.belot.enums.Team;
import java.util.Optional;

/**
 * Where a game stands after a deal: over, or another deal to play.
 *
 * @param winner the team that has won, or null while the game goes on
 */
public record GameVerdict(Team winner) {

    public static GameVerdict playOn() {
        return new GameVerdict(null);
    }

    public static GameVerdict wonBy(Team team) {
        return new GameVerdict(team);
    }

    public boolean isFinished() {
        return winner != null;
    }

    public Optional<Team> winningTeam() {
        return Optional.ofNullable(winner);
    }
}
