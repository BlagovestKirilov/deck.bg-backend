package bg.deck.common.model.response;

import bg.deck.common.model.dto.GameStatsDTO;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * One friend as the list shows them: whether they are here, the game they are
 * playing if they are, and their record at each game, keyed by its code.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FriendView(
        String username,
        boolean online,
        /** The code of the game they are in now, or null. */
        String playing,
        Map<String, GameStatsDTO> ranks
) {
}
