package bg.deck.common.enums;

/**
 * What happened among a player's friends, as their friends topic says it. The
 * screen reads the list again on any of them; the type says what to show.
 */
public enum FriendEventType {
    /** Somebody asked to be this player's friend. */
    REQUEST,
    /** A request this player made was accepted. */
    ACCEPTED,
    /** A friendship or a request was ended, by either side. */
    REMOVED,
    /** A friend challenges this player to a game. */
    CHALLENGE,
    /** The friend this player challenged said no. */
    CHALLENGE_DECLINED,
    /** The friend who challenged this player took it back. */
    CHALLENGE_CANCELLED,
    /** Nobody answered the challenge in time. */
    CHALLENGE_EXPIRED,
    /** The challenge was accepted and the game is on. */
    GAME_STARTED
}
