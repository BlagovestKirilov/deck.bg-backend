package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Seat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Playing a deal out, trick by trick.
 *
 * <p>Whoever takes a trick leads the next. Every card offered by the chooser is
 * checked against {@link LegalMoves} before it is played: a chooser is a
 * player, and a player can be wrong.
 */
public final class DealPlay {

    private DealPlay() {
    }

    public static PlayedDeal play(Map<Seat, List<Card>> hands, Contract contract, Seat leader, CardChooser chooser) {
        Map<Seat, List<Card>> remaining = new EnumMap<>(Seat.class);
        hands.forEach((seat, cards) -> remaining.put(seat, new ArrayList<>(cards)));

        List<Trick> tricks = new ArrayList<>(Dealing.HAND_SIZE);
        List<Seat> winners = new ArrayList<>(Dealing.HAND_SIZE);
        Seat toLead = leader;

        for (int round = 0; round < Dealing.HAND_SIZE; round++) {
            Trick trick = Trick.empty();
            Seat seat = toLead;

            for (int turn = 0; turn < Seat.values().length; turn++) {
                List<Card> hand = remaining.get(seat);
                List<Card> legal = LegalMoves.of(hand, trick, seat, contract);
                Card chosen = chooser.choose(seat, legal, trick);

                if (!legal.contains(chosen)) {
                    throw new IllegalArgumentException(seat + " cannot play " + chosen + "; legal: " + legal);
                }
                hand.remove(chosen);
                trick = trick.with(new Play(seat, chosen));
                seat = seat.next();
            }

            Seat winner = TrickResolver.leader(trick, contract).orElseThrow();
            tricks.add(trick);
            winners.add(winner);
            toLead = winner;
        }
        return new PlayedDeal(tricks, winners);
    }
}
