package bg.deck.common.model.response;

import java.time.Instant;
import java.util.UUID;

/** A challenge to a game between two friends, open until it is answered or runs out. */
public record ChallengeView(
        UUID id,
        String from,
        String to,
        String game,
        Instant expiresAt
) {
}
