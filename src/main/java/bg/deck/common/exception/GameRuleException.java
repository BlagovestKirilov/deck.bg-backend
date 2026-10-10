package bg.deck.common.exception;

/**
 * A move a game's rules refuse: not your turn, a card you may not play, a
 * deck the wrong size for what you asked.
 *
 * <p>The one thing the shared error handler knows about the games. Each
 * game's own exceptions extend it, so the handler answers every one of them
 * with the same 400 without importing a single game package — which is what
 * keeps {@code common} free of the games that stand on it.
 */
public abstract class GameRuleException extends RuntimeException {

    protected GameRuleException(String message) {
        super(message);
    }
}
