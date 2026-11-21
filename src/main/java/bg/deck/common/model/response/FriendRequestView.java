package bg.deck.common.model.response;

import java.util.UUID;

/** A friend request waiting for an answer, and the other player in it. */
public record FriendRequestView(UUID id, String username) {
}
