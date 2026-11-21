package bg.deck.common.enums;

/**
 * Where a friendship stands. Nothing is deleted: a request that was turned
 * down, taken back or a friendship that was ended keeps its row, with how it
 * ended. Only PENDING and ACCEPTED count; asking again later reuses the row.
 */
public enum FriendshipStatus {
    /** Asked for, waiting for the other player's answer. */
    PENDING,
    /** Agreed to: the two are friends. */
    ACCEPTED,
    /** The player asked said no. */
    REJECTED,
    /** The player who asked took it back before an answer. */
    CANCELLED,
    /** They were friends, and one of them ended it. */
    REMOVED
}
