package bg.deck.santase.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CardDTO(
        UUID id,
        String suit,
        String rank,
        int points,
        @JsonProperty("isPlayable")
        boolean isPlayable,
        @JsonProperty("isLastDrawn")
        boolean isLastDrawn
) {
}
