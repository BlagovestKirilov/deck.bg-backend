package bg.deck.santase.model.request;

import bg.deck.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CardRequest(
        @NotNull(message = ValidationConstants.CARD_ID_NULL)
        UUID cardId
) {
}
