package bg.deck.model.event;

/**
 * An account has been deleted and its rows anonymised.
 *
 * <p>Published so that parts of the service which keep their own tables can
 * clear what they hold without the deletion code having to know they exist —
 * belot keeps its own schema and is reached this way rather than by a call
 * across the seam.
 *
 * <p>Carries the username and nothing else: it is what belot stores, and the
 * less that travels in an event the less there is to keep in step.
 *
 * @param username the name the deleted account played under
 */
public record UserDeleted(String username) {
}
