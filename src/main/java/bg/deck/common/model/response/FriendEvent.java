package bg.deck.common.model.response;

import bg.deck.common.enums.FriendEventType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Something that happened among this player's friends, on their friends topic.
 * Only the fields the type needs are set.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FriendEvent(
        FriendEventType type,
        /** Who did it. */
        String from,
        /** The challenge, for the challenge events. */
        ChallengeView challenge,
        /** The game started, for GAME_STARTED: its code and its id. */
        String game,
        UUID gameId
) {

    public static FriendEvent of(FriendEventType type, String from) {
        return new FriendEvent(type, from, null, null, null);
    }

    public static FriendEvent about(FriendEventType type, ChallengeView challenge, String from) {
        return new FriendEvent(type, from, challenge, null, null);
    }

    public static FriendEvent started(String game, UUID gameId, String from) {
        return new FriendEvent(FriendEventType.GAME_STARTED, from, null, game, gameId);
    }
}
