package bg.deck.belot.model;

/** How a game ended before its last hand: given up, or left to run out. */
public enum BelotForfeit {
    /** A player pressed "give up". */
    SURRENDER,
    /** A player let their time run out three times. */
    INACTIVITY
}
