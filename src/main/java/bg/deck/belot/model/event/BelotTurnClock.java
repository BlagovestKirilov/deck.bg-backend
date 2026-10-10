package bg.deck.belot.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a table's turn clock stands after a change: which deal is on, and
 * when the seat now to act runs out.
 *
 * <p>Published by {@code BelotService} every time it tells a table what
 * happened, and read by {@code BelotTurnTimer} once that change has
 * committed. Both may be null: a table between hands, or one that is over,
 * has no clock to keep.
 *
 * @param tableId  the table
 * @param dealId   the deal now in progress, or null
 * @param deadline when the seat now to act runs out, or null if nobody is to act
 */
public record BelotTurnClock(UUID tableId, UUID dealId, Instant deadline) {

    public boolean isRunning() {
        return dealId != null && deadline != null;
    }
}
