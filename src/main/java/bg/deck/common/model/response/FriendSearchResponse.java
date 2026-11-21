package bg.deck.common.model.response;

import java.util.List;

/**
 * Players found by the start of their name, to ask to be friends.
 *
 * <p>At most a handful, and only when they are all of them: where more names
 * start with what was typed, {@code more} is set and the list holds no more
 * than the one name typed out in full, if there is one — the screen asks for
 * more letters rather than showing a few picked by the alphabet.
 */
public record FriendSearchResponse(
        List<FriendSearchHit> players,
        boolean more
) {
}
