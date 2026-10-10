package bg.deck.common.enums;

/**
 * How far into the site an account can see.
 *
 * <p>A ladder rather than a label: a scope covers itself and everything below
 * it, so a beta tester keeps the ordinary games as well as whatever they are
 * testing. Adding a level above is an enum value and a line of check constraint
 * — the comparison below does not change.
 */
public enum Scope {

    /** Everyone. */
    PUBLIC,
    /** The few who are meant to see something before the rest. */
    BETA;

    /** True when this scope reaches whatever {@code required} asks for. */
    public boolean covers(Scope required) {
        return ordinal() >= required.ordinal();
    }
}
