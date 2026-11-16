package bg.deck.common.enums;

/**
 * What a player can send the table instead of words: a fixed set, so there
 * is nothing to moderate and nothing to type. Shared by every game.
 *
 * <p>The client draws each one; the server only checks it is one of these.
 */
public enum Reaction {
    THUMBS_UP,
    CLAP,
    LAUGH,
    WOW,
    SAD,
    ANGRY,
    LUCK,
    RED_CARD,
    MOUSE
}
