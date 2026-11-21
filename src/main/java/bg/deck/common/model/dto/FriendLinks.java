package bg.deck.common.model.dto;

import bg.deck.common.model.response.FriendRequestView;

import java.util.List;

/**
 * Everyone a player is linked to: their friends by name, the requests waiting
 * for their answer, and the ones they sent that are waiting for somebody else's.
 */
public record FriendLinks(
        List<String> friends,
        List<FriendRequestView> incoming,
        List<FriendRequestView> outgoing
) {
}
