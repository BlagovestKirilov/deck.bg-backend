package bg.deck.common.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String status,
        String message,
        String token,
        String refreshToken
) {
}
