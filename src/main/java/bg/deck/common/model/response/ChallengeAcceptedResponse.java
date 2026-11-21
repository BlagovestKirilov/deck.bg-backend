package bg.deck.common.model.response;

import java.util.UUID;

/** The game an accepted challenge started: its code, and its id. */
public record ChallengeAcceptedResponse(String game, UUID gameId) {
}
