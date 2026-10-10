package bg.deck.belot.engine;

import bg.deck.belot.enums.DeclarationKind;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which team's declarations count, and for how much. RULES §7.
 *
 * <p>Sequences are a contest: only the team holding the best one scores, and it
 * scores all of its sequences. Length decides, then the top card; if the two
 * best are the same on both counts, every sequence on the table is cancelled —
 * "всички премии за поредни карти отпадат".
 *
 * <p>OPEN 5 — carrés are compared on their own here, not against sequences, as
 * in classic belote. OPEN 6 — a belote always scores, whoever holds the best of
 * anything else. OPEN 7 — carrés rank by the trump order, J 9 A 10 K Q, so four
 * jacks beat four nines.
 */
public final class DeclarationScoring {

    private DeclarationScoring() {
    }

    /** What the first team scores, given what the other holds. */
    public static int scoreFor(List<Declaration> ours, List<Declaration> theirs) {
        return sequencePoints(ours, theirs) + carrePoints(ours, theirs) + belotePoints(ours);
    }

    private static int sequencePoints(List<Declaration> ours, List<Declaration> theirs) {
        Optional<Declaration> best = bestSequence(ours);
        if (best.isEmpty()) {
            return 0;
        }
        Optional<Declaration> rival = bestSequence(theirs);
        if (rival.isPresent() && compareSequences(best.get(), rival.get()) <= 0) {
            // Beaten, or equal and therefore cancelled for both.
            return 0;
        }
        return ours.stream().filter(Declaration::isSequence).mapToInt(Declaration::points).sum();
    }

    private static int carrePoints(List<Declaration> ours, List<Declaration> theirs) {
        Optional<Declaration> best = bestCarre(ours);
        if (best.isEmpty()) {
            return 0;
        }
        Optional<Declaration> rival = bestCarre(theirs);
        if (rival.isPresent() && carreStrength(rival.get()) > carreStrength(best.get())) {
            return 0;
        }
        return ours.stream()
                .filter(declaration -> declaration.kind() == DeclarationKind.CARRE)
                .mapToInt(Declaration::points)
                .sum();
    }

    private static int belotePoints(List<Declaration> ours) {
        return ours.stream()
                .filter(declaration -> declaration.kind() == DeclarationKind.BELOTE)
                .mapToInt(Declaration::points)
                .sum();
    }

    static Optional<Declaration> bestSequence(List<Declaration> declarations) {
        return declarations.stream()
                .filter(Declaration::isSequence)
                .max(DeclarationScoring::compareSequences);
    }

    private static Optional<Declaration> bestCarre(List<Declaration> declarations) {
        return declarations.stream()
                .filter(declaration -> declaration.kind() == DeclarationKind.CARRE)
                .max(Comparator.comparingInt(DeclarationScoring::carreStrength));
    }

    /** Longer wins; equal length is settled by the top card. */
    static int compareSequences(Declaration one, Declaration other) {
        int byLength = Integer.compare(one.points(), other.points());
        return byLength != 0
                ? byLength
                : Integer.compare(one.topRank().naturalOrder(), other.topRank().naturalOrder());
    }

    /** J 9 A 10 K Q — the trump order, as carrés are usually ranked. */
    private static int carreStrength(Declaration carre) {
        return carre.topRank().strength(true);
    }
}
