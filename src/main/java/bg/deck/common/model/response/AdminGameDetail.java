package bg.deck.common.model.response;

import bg.deck.common.model.dto.AdminGameRecord;
import bg.deck.common.model.dto.AdminGameRow;

import java.util.List;
import java.util.UUID;

/**
 * One game's view of a player, for the admin page.
 *
 * @param code         the game
 * @param record       their record at it
 * @param activeGameId the game they are in now, or null
 * @param recent       their last finished games, newest first
 */
public record AdminGameDetail(String code, AdminGameRecord record, UUID activeGameId, List<AdminGameRow> recent) {
}
