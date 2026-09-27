package bg.deck.belot.model.response;

import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.DealResult;
import bg.deck.belot.engine.Doubling;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;

/**
 * One line of the score sheet: a hand that has been played and counted.
 *
 * <p>Both halves of it are here — what the cards were worth and what went on
 * the sheet — because those are different numbers and a player checking the
 * score is usually asking about the gap between them.
 *
 * @param dealNumber      which hand, counted from one
 * @param contract        what was bid
 * @param declarer        who bid it
 * @param callerTeam      the side that bid it
 * @param doubling        plain, contra'd or recontra'd
 * @param callerPoints    card points the calling side took
 * @param opponentPoints  card points the other side took
 * @param callerScore     game points written down for the calling side
 * @param opponentScore   game points written down for the other side
 * @param result          made, вътре or висящи
 */
public record BelotDealRow(
        int dealNumber,
        Contract contract,
        Seat declarer,
        Team callerTeam,
        Doubling doubling,
        Integer callerPoints,
        Integer opponentPoints,
        Integer callerScore,
        Integer opponentScore,
        DealResult result
) {
}
