package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Team;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What each team took from a played deal: the cards, the last trick, capot, and
 * whatever was declared.
 *
 * <p>The cards and the last trick always come to the contract's own total — 162,
 * 258 or 260 — and that identity is what the self-play fuzz leans on.
 */
public final class DealPoints {

    private DealPoints() {
    }

    /** Cards taken in tricks, plus the ten for the last one. */
    public static Map<Team, Integer> fromTricks(PlayedDeal played, Contract contract) {
        Map<Team, Integer> points = new EnumMap<>(Team.class);
        points.put(Team.NORTH_SOUTH, 0);
        points.put(Team.EAST_WEST, 0);

        for (int i = 0; i < played.tricks().size(); i++) {
            Team winner = Team.of(played.winners().get(i));
            int taken = played.tricks().get(i).plays().stream()
                    .mapToInt(play -> CardPoints.of(play.card(), contract))
                    .sum();
            points.merge(winner, taken, Integer::sum);
        }
        points.merge(Team.of(played.lastTrickWinner()), CardPoints.lastTrick(contract), Integer::sum);
        return points;
    }

    /**
     * Everything: the tricks, capot, and the declarations each team is allowed
     * to score.
     *
     * <p>OPEN 18 — capot is added at 90 whatever the contract. Every other point
     * in a no-trump deal counts double, so this may be 180 there.
     */
    public static Map<Team, Integer> total(PlayedDeal played,
                                           Contract contract,
                                           List<Declaration> northSouth,
                                           List<Declaration> eastWest) {
        Map<Team, Integer> points = fromTricks(played, contract);

        Team capot = played.capotBy();
        if (capot != null) {
            points.merge(capot, CardPoints.CAPOT, Integer::sum);
        }

        points.merge(Team.NORTH_SOUTH, DeclarationScoring.scoreFor(northSouth, eastWest), Integer::sum);
        points.merge(Team.EAST_WEST, DeclarationScoring.scoreFor(eastWest, northSouth), Integer::sum);
        return points;
    }
}
