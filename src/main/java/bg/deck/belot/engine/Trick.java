package bg.deck.belot.engine;

import bg.deck.belot.enums.Suit;
import java.util.List;
import java.util.Optional;

/**
 * The cards on the table, in the order they were played.
 *
 * <p>Empty while the leader is still to play. The led suit is the first card's
 * — everything about what may follow is measured against it.
 *
 * @param plays the cards so far, oldest first
 */
public record Trick(List<Play> plays) {

    public Trick {
        plays = List.copyOf(plays);
    }

    public static Trick empty() {
        return new Trick(List.of());
    }

    public Trick with(Play play) {
        return new Trick(java.util.stream.Stream.concat(plays.stream(), java.util.stream.Stream.of(play)).toList());
    }

    public boolean isEmpty() {
        return plays.isEmpty();
    }

    /** The suit that was led, or empty while nobody has played. */
    public Optional<Suit> ledSuit() {
        return plays.isEmpty() ? Optional.empty() : Optional.of(plays.getFirst().card().suit());
    }
}
