package bg.deck.tabla.model.dto;

/**
 * One throw of the opening roll, from one player's side of the board: each
 * player throws a single die and the higher starts. Equal dice are thrown
 * again, so a game can open with several of these, the last one deciding.
 *
 * @param mine     the die this player threw
 * @param opponent the die their opponent threw
 */
public record OpeningThrowDTO(int mine, int opponent) {
}
