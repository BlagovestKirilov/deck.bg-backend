package bg.deck.common.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A player's friends page: their friends, the requests either way, and the
 * challenge open to or from them, if there is one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FriendsResponse(
        List<FriendView> friends,
        List<FriendRequestView> incoming,
        List<FriendRequestView> outgoing,
        ChallengeView incomingChallenge,
        ChallengeView outgoingChallenge
) {
}
