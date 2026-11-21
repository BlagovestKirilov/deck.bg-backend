package bg.deck.common.enums;

/** Where a player found by a search stands with the one searching. */
public enum FriendRelation {
    /** Nothing between them: they can be asked. */
    NONE,
    /** Asked by the one searching; waiting for their answer. */
    SENT,
    /** They asked the one searching: adding them accepts it. */
    RECEIVED
}
