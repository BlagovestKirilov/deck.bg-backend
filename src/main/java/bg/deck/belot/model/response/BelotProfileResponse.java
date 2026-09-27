package bg.deck.belot.model.response;

/**
 * A player’s belot record.
 *
 * <p>Its own endpoint rather than a section of {@code /user/profile}: belot
 * keeps its own tables, and the profile page asks both and puts them
 * together. The shape matches santase’s {@code GameStatsDTO} on purpose,
 * so one card on the profile page can render either.
 *
 * <p>The rating behind the rank is not sent, for the same reason santase
 * does not send it: players are shown where they stand, not the number
 * that decided it.
 *
 * @param games                   games played to the end
 * @param wins                    games their pair took
 * @param losses                  games their pair lost
 * @param rank                    where they stand, UNRANKED until placement is done
 * @param placementGamesRemaining games still needed before a rank is given
 */
public record BelotProfileResponse(
        int games,
        int wins,
        int losses,
        String rank,
        int placementGamesRemaining
) {
}
