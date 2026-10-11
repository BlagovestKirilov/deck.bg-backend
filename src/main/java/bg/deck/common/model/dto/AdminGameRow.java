package bg.deck.common.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One finished game of a player's, for the admin page.
 *
 * @param gameId      the game
 * @param finishedAt  when it ended
 * @param others      everybody else who sat at it
 * @param won         whether the player's side won
 * @param surrendered whether the player gave it up — surrendered, or let
 *                    their time run out until it was forfeited
 */
public record AdminGameRow(UUID gameId, Instant finishedAt, List<String> others, boolean won, boolean surrendered) {
}
