package bg.deck.tabla.model.dto;

import bg.deck.tabla.engine.ComboHop;

import java.util.List;

/**
 * One checker's move using several dice, offered as a single destination.
 *
 * <p>The client plays it as the hops it really is — {@code from} with the first
 * die, then each {@code via} with the next — so the server needs no new
 * endpoint and every hop can still be taken back one at a time.
 *
 * @param vias the intermediate points, in order; one fewer than {@code dice}
 */
public record ComboHopDTO(int from, int to, List<Integer> vias, List<Integer> dice) {
    public static ComboHopDTO from(ComboHop hop) {
        return new ComboHopDTO(hop.from(), hop.to(), hop.vias(), hop.dice());
    }
}
