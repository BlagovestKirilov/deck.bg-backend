package bg.deck.common.enums;

import bg.deck.common.model.Game;

/**
 * Which game a {@link Game} row belongs to.
 *
 * <p>Every pre-existing row is {@code SANTASE}; the backfill in changeset 010
 * sets that explicitly before the column is made NOT NULL.
 */
public enum GameType {
    SANTASE,
    TABLA
}
