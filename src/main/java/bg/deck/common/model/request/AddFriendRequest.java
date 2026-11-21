package bg.deck.common.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Asks a player, by username, to be friends. */
public record AddFriendRequest(
        @NotBlank @Size(max = 64) String username
) {
}
