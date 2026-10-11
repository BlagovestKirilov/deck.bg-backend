package bg.deck.common.service;

import bg.deck.common.model.dto.AdminGameRecord;
import bg.deck.common.model.dto.AdminGameRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What one game tells the admin page about a player.
 *
 * <p>Implemented by each game, as {@link GameRecordProvider} is, so common can
 * ask every game without knowing which games there are. Reads only: looking a
 * player up never writes a record or a seat.
 */
public interface GameAdminView {

    /** The game's code, as in the availability table. */
    String code();

    /** Their record; a fresh one, unsaved, for a player who has not played. */
    AdminGameRecord recordOf(String username);

    /** The game they are in now, if any. */
    Optional<UUID> activeGameId(String username);

    /** Their last finished games, newest first. */
    List<AdminGameRow> recentGames(String username, int limit);
}
