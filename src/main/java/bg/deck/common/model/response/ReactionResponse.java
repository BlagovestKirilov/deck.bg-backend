package bg.deck.common.model.response;

import bg.deck.common.enums.Reaction;

/**
 * A reaction, as everyone at the table is sent it — the sender included, so
 * every screen shows it from the same message.
 *
 * @param from     who sent it, by username
 * @param reaction what they sent
 */
public record ReactionResponse(String from, Reaction reaction) {
}
