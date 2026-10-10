package bg.deck.belot.engine;

import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;
import java.util.List;

/**
 * Eight tricks, played out.
 *
 * @param tricks  the tricks in order
 * @param winners who took each of them, in the same order
 */
public record PlayedDeal(List<Trick> tricks, List<Seat> winners) {

    public PlayedDeal {
        tricks = List.copyOf(tricks);
        winners = List.copyOf(winners);
    }

    public Seat lastTrickWinner() {
        return winners.getLast();
    }

    /** The team that took every trick, if one did. */
    public Team capotBy() {
        Team first = Team.of(winners.getFirst());
        return winners.stream().allMatch(seat -> Team.of(seat) == first) ? first : null;
    }
}
