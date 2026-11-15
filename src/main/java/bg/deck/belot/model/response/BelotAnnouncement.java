package bg.deck.belot.model.response;

import java.util.List;

/**
 * One way a player may announce their hand, offered with their first card.
 *
 * <p>The player's own hand, so all of it is shown — suit, top card, points —
 * to the one player it is sent to.
 *
 * @param key          what to send back with the card to choose it
 * @param declarations what it announces; empty for announcing nothing
 */
public record BelotAnnouncement(String key, List<BelotDeclarationView> declarations) {

    public BelotAnnouncement {
        declarations = List.copyOf(declarations);
    }
}
