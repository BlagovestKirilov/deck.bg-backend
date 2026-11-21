package bg.deck.common.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Challenges a friend, by username, to a game, by its code (SANTASE, TABLA). */
public record ChallengeRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 20) String game
) {
}
