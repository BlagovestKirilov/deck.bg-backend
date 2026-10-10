package bg.deck.common.model.dto;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Every game on offer, as the table had them when it was last read.
 *
 * <p>This is what sits in the cache, so it is a value and not a view: the map
 * is copied on the way in and cannot be edited afterwards, by the reader who
 * gets it or by anybody holding a reference from before.
 *
 * <p>Looking a game up and walking the lot are both asked of it here rather
 * than handing the map out, so a caller never has to know that a missing game
 * arrives as {@code null} from a map — it arrives as an empty {@link Optional},
 * and the reason a code is unknown stays one answer instead of two.
 *
 * @param byCode the games, by their code
 */
public record CachedServices(Map<String, ServiceAvailability> byCode) {

    public CachedServices {
        byCode = Map.copyOf(byCode);
    }

    /** The game with this exact code, if the table has one. */
    public Optional<ServiceAvailability> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    /** Every game the table knows about, offered or not. */
    public Collection<ServiceAvailability> all() {
        return byCode.values();
    }
}
