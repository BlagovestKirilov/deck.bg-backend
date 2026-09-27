package bg.deck.belot.engine;

import java.util.List;
import java.util.Optional;

/**
 * What a seat is allowed to play, given what is already on the table.
 *
 * <p>The rules, from RULES §6: follow the led suit if you hold it; if you do
 * not and <em>an opponent</em> is winning the trick, you must trump, and
 * overtrump if an opponent has already trumped. When your own partner holds the
 * trick there is no obligation at all — "ако взятката до момента принадлежи на
 * противника" is the whole of the condition.
 *
 * <p>One branch the rules page does not settle is marked below as OPEN 12 and
 * implemented the usual Bulgarian way; {@code BelotTrickTest} names it.
 */
public final class LegalMoves {

    private LegalMoves() {
    }

    public static List<Card> of(List<Card> hand, Trick trick, Seat seat, Contract contract) {
        Optional<Suit> led = trick.ledSuit();
        if (led.isEmpty()) {
            return hand;
        }

        List<Card> followers = suit(hand, led.get());
        boolean opponentHoldsIt = TrickResolver.leader(trick, contract)
                .map(seat::isOpponentOf)
                .orElse(false);

        if (!followers.isEmpty()) {
            // OPEN 12 — when the led suit is the one played by the trump order,
            // the usual rule is that you must beat what is on the table if you
            // can. Applied here only while an opponent holds the trick, for the
            // same reason the trumping obligation is.
            if (contract.isTrump(led.get()) && opponentHoldsIt) {
                List<Card> better = beating(followers, trick, contract);
                return better.isEmpty() ? followers : better;
            }
            return followers;
        }

        // Void in the led suit. With a partner winning, anything goes.
        if (!opponentHoldsIt) {
            return hand;
        }

        // Nothing to trump with in all trumps or no trumps: neither has a suit
        // that beats another, so being void is simply being free.
        Optional<Suit> trumpSuit = contract.trumpSuit();
        if (trumpSuit.isEmpty()) {
            return hand;
        }

        List<Card> trumps = suit(hand, trumpSuit.get());
        if (trumps.isEmpty()) {
            return hand;
        }

        List<Card> overtrumps = beating(trumps, trick, contract);
        if (!overtrumps.isEmpty()) {
            return overtrumps;
        }

        // ANSWERED (OPEN 13) — holding trumps but none high enough. An opponent
        // has trumped and cannot be beaten: "в случай че няма по-висок коз,
        // може да изиграе произволна карта". Nobody is made to waste a trump on
        // a trick that is already lost.
        //
        // This branch is reached only when a trump is already on the table: with
        // none there, every trump beats the leader and the list above is not
        // empty. So "no overtrump" and "an opponent has trumped" are the same
        // condition, which is why there is no separate check for it.
        return hand;
    }

    private static List<Card> suit(List<Card> hand, Suit suit) {
        return hand.stream().filter(card -> card.suit() == suit).toList();
    }

    private static List<Card> beating(List<Card> candidates, Trick trick, Contract contract) {
        return candidates.stream().filter(card -> TrickResolver.beatsAll(card, trick, contract)).toList();
    }
}
