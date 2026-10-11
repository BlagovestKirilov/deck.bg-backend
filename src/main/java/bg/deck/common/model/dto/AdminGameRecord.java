package bg.deck.common.model.dto;

/**
 * A player's record at one game, as the admin page shows it: the rating as
 * well as the rank it earns.
 */
public record AdminGameRecord(int wins, int losses, int rating, String rank) {
}
