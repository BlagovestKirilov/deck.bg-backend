package bg.deck.tabla.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One checker move. Only the origin and the die are sent — the destination is
 * derived server-side so a client cannot desync the board.
 */
public record MoveRequest(
        /** 1..24 for a point, 25 for the bar. */
        @NotNull(message = "Полето 'from' е задължително.")
        @Min(value = 1, message = "Невалидно поле.")
        @Max(value = 25, message = "Невалидно поле.")
        Integer from,

        @NotNull(message = "Полето 'die' е задължително.")
        @Min(value = 1, message = "Невалиден зар.")
        @Max(value = 6, message = "Невалиден зар.")
        Integer die
) {
}
