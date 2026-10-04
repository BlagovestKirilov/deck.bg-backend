package bg.deck.belot.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Where the player cutting touched the deck.
 *
 * @param at the card the top part is lifted from, counted from the top of a
 *           deck of 32; never the very top or bottom, which would not be a cut
 */
public record BelotCutRequest(@NotNull @Min(1) @Max(31) Integer at) {
}
