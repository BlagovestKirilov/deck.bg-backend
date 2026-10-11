package bg.deck.common.model.response;

import java.util.List;

/** A player, and what each game knows about them. */
public record AdminPlayerDetail(AdminPlayerRow account, List<AdminGameDetail> games) {
}
