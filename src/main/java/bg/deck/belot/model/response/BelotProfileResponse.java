package bg.deck.belot.model.response;

/**
 * A player's belot record.
 *
 * <p>Its own endpoint rather than a section of {@code /user/profile}: belot
 * keeps its own tables, and the profile page asks both and puts them
 * together. No rank and no rating — see {@code docs/belot/BUILD.md} for the
 * question that has to be answered before there can be one.
 *
 * @param games  games played to the end
 * @param wins   games their pair took
 * @param losses games their pair lost
 */
public record BelotProfileResponse(int games, int wins, int losses) {
}
