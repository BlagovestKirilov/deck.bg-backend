package bg.deck.belot.model.response;

import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;

/**
 * One place at the table, as everyone may see it.
 *
 * <p>{@code cardsLeft} is how many cards they still hold, which everyone at
 * a real table can see by looking. Which cards those are is not here.
 *
 * @param seat      where it is
 * @param team      which pair it belongs to
 * @param username  who is sitting there
 * @param cardsLeft   how many cards they are still holding
 * @param missedTurns how many times the table has had to play for them this
 *                    game; the third gives the game away
 */
public record BelotSeatView(Seat seat, Team team, String username, int cardsLeft, int missedTurns) {
}
