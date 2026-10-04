package bg.deck.common.model.dto;

/**
 * One game's record for a player.
 *
 * <p>The Elo rating itself is deliberately not here. It still drives ranking
 * server-side, but it is an implementation detail of how a rank is decided —
 * players are shown the rank, not the number behind it.
 *
 * @param placementGamesRemaining games still needed before a rank is assigned.
 *                                Sent by the server so the client stops
 *                                duplicating the placement threshold.
 */
public record GameStatsDTO(
        int wins,
        int losses,
        String rank,
        int placementGamesRemaining
) {
}
