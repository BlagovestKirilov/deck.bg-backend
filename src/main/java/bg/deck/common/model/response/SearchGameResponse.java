package bg.deck.common.model.response;

import bg.deck.common.enums.SearchGameStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchGameResponse(
        String status,
        UUID gameId
) {
    public static SearchGameResponse waiting() {
        return new SearchGameResponse(SearchGameStatus.WAITING.toString(), null);
    }

    public static SearchGameResponse started(UUID gameId) {
        return new SearchGameResponse(SearchGameStatus.GAME_STARTED.toString(), gameId);
    }
}
