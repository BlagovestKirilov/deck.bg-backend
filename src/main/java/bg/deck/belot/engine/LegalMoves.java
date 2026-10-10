package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Suit;
import java.util.List;
import java.util.Optional;

/**
 * What a seat is allowed to play, given what is already on the table.
 *
 * <p>The rules, from RULES §6: follow the led suit if you hold it, and when
 * that suit is played by the trump order, go higher than what is on the table
 * if you can — качване, whoever holds the trick. If you cannot follow and
 * <em>an opponent</em> is winning, you must trump, and overtrump if an opponent
 * has already trumped; when your own partner holds it you are free — "ако
 * взятката до момента принадлежи на противника" is the whole of that condition.
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
            // ANSWERED (OPEN 12) — качване. When trumps are led you must go
            // higher than what is on the table if you can, and it does not
            // matter who is holding the trick: your partner's queen of trumps is
            // raised with your king just as an opponent's is. It used to apply
            // only while an opponent held the trick, borrowed from the trumping
            // rule below, and that let a seven of trumps go under a partner's
            // queen with the jack, the ace and the king all in the hand.
            //
            // In all trumps every suit is played by the trump order, so every
            // suit is raised. In no trumps none is: you follow, and that is all.
            if (contract.isTrump(led.get())) {
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
