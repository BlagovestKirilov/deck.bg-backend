package bg.deck.tabla.enums;

/**
 * How decisively a game was won. Display only — a win is one win in
 * {@code user_game_stats} regardless, per the product decision to keep
 * Обикновена табла to a single game with no doubling cube.
 */
public enum GameResultKind {
    /** The loser bore off at least one checker. */
    SINGLE,
    /** Марс — the loser bore off nothing. */
    GAMMON,
    /** Кокс — марс, and the loser still has a checker on the bar or in the winner's home. */
    BACKGAMMON
}
