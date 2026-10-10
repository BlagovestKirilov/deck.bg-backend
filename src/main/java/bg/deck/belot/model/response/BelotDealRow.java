package bg.deck.belot.model.response;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.DealResult;
import bg.deck.belot.enums.Doubling;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;

/**
 * One line of the score sheet: a hand that has been played and counted.
 *
 * <p>Both halves of it are here — what the cards were worth and what went on
 * the sheet — because those are different numbers and a player checking the
 * score is usually asking about the gap between them.
 *
 * <p>The announcements are counted out separately for the same reason. They
 * are inside the card points, so the sheet adds up either way; but a терца is
 * called out at the table and then disappears into a total, and the hand it
 * was worth twenty of is the one a player asks about afterwards.
 *
 * @param dealNumber      which hand, counted from one
 * @param contract        what was bid
 * @param declarer        who bid it
 * @param callerTeam      the side that bid it
 * @param doubling        plain, contra'd or recontra'd
 * @param callerPoints    card points the calling side took
 * @param opponentPoints  card points the other side took
 * @param callerDeclarations   what the calling side's announcements were worth
 * @param opponentDeclarations what the other side's were worth
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
        int callerDeclarations,
        int opponentDeclarations,
        Integer callerScore,
        Integer opponentScore,
        DealResult result
) {
}
