package bg.deck.common.service;

import bg.deck.common.model.dto.GameStatsDTO;

/**
 * One game's record for a player, as the profile page shows it.
 *
 * <p>Implemented by each game, in its own package, and handed to the profile
 * as a list: so the profile can show every game without knowing which games
 * there are, and a new game is one more implementation rather than a branch in
 * common. Ordered with {@code @Order}, which is the order the profile lists
 * them in.
 */
public interface GameRecordProvider {

    /** The game's code as the catalogue spells it, and the key its record is filed under. */
    String code();

    /** This player's record at this game. */
    GameStatsDTO recordOf(String username);
}
