package bg.deck.common.model.response;

import bg.deck.common.enums.FriendRelation;

/** A player found by a search, and where they stand with the one searching. */
public record FriendSearchHit(String username, FriendRelation relation) {
}
