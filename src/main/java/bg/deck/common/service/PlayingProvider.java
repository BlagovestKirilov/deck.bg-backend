package bg.deck.common.service;

/**
 * Whether a player is at one game's table now, asked by the friends list.
 *
 * <p>Implemented by each game in its own package, as {@link GameRecordProvider}
 * is, so the list can say "in a game" without {@code common} knowing which
 * games there are.
 */
public interface PlayingProvider {

    /** The game's code as the catalogue spells it. */
    String code();

    /** True while this player has a game of this kind in progress. */
    boolean isPlaying(String username);
}
