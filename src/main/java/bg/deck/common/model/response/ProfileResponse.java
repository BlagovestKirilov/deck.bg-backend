package bg.deck.common.model.response;

import bg.deck.common.model.dto.GameStatsDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        /**
         * Kept, and still populated from the SANTASE stats row, purely so the
         * currently deployed frontend keeps working. New clients should read
         * {@link #stats} instead.
         */
        int santaseWins,
        int santaseLosses,
        String rank,

        @JsonProperty("isEmailConfirmed")
        boolean emailConfirmed,

        /** Per-game record, keyed by game type: SANTASE, TABLA. */
        Map<String, GameStatsDTO> stats
) {
}
