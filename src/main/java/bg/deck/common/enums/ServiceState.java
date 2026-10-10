package bg.deck.common.enums;

/**
 * Whether a game is being offered at all.
 *
 * <p>{@code OFF} stops new games; the tables already being played finish. A
 * state that ends those too can be added the day an incident needs one.
 */
public enum ServiceState {
    ON, OFF
}
