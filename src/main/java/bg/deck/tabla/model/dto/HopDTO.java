package bg.deck.tabla.model.dto;

import bg.deck.tabla.engine.Hop;
import bg.deck.tabla.engine.MoverView;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One legal move, in the mover's own frame.
 *
 * @param from 1..24, or 25 for the bar
 * @param to   1..24, or 0 for bearing off
 */
public record HopDTO(
        int from,
        int to,
        int die,
        @JsonProperty("isHit") boolean isHit,
        @JsonProperty("isBearOff") boolean isBearOff,
        @JsonProperty("isEntry") boolean isEntry
) {
    public static HopDTO from(Hop hop) {
        return new HopDTO(hop.from(), hop.to(), hop.die(), hop.hit(), hop.isBearOff(), hop.isEntry());
    }

    public static int barIndex() {
        return MoverView.BAR;
    }
}
