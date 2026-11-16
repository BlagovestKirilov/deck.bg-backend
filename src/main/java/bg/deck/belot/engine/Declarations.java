package bg.deck.belot.engine;

import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.DeclarationKind;
import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Suit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a hand holds worth declaring. RULES §7.
 *
 * <p>Sequences run by the <em>natural</em> order of the cards — 7 8 9 10 J Q K A
 * — and not by the trump order that decides tricks. A jack sits between the ten
 * and the queen here however mighty it is in play.
 *
 * <p>Only the longest run in a suit counts: four in a row is one quarte, never
 * two overlapping terzes.
 */
public final class Declarations {

    /** Shortest run worth saying. */
    private static final int SHORTEST_SEQUENCE = 3;

    private Declarations() {
    }

    /**
     * Everything in this hand that may be declared under this contract.
     *
     * <p>Nothing at all in no trumps: "играчите нямат право да обявяват
     * притежаваните от тях комбинации".
     */
    public static List<Declaration> in(List<Card> hand, Contract contract) {
        return in(hand, contract, null);
    }

    /**
     * What this hand declares, as its player chose to announce it.
     *
     * @param chosen the {@link #key} of what the player ticked with their first
     *               card — see {@link #choose}; null, or a choice this hand
     *               cannot make, takes the richest — what a player who was
     *               never asked would want
     */
    public static List<Declaration> in(List<Card> hand, Contract contract, String chosen) {
        if (contract == Contract.NO_TRUMPS) {
            return List.of();
        }

        List<Declaration> found = new ArrayList<>(choose(hand, contract, chosen)
                .orElseGet(() -> options(hand, contract).getFirst()));
        // A belote is not in the contest: it is two named cards of the trump
        // suit and it always scores, so it never gives way to anything.
        found.addAll(belotes(hand, contract));
        return List.copyOf(found);
    }

    /** The key of announcing nothing at all. */
    public static final String NOTHING = "NONE";

    /**
     * The declarations a key names, if this hand can announce them together:
     * any of them, as long as no card serves two — any part of one of its
     * {@link #options}. The player ticks them one by one, so a terz on its own
     * out of a carré and a terz is a choice too.
     *
     * @return empty for a key this hand cannot announce, and for no key
     */
    public static Optional<List<Declaration>> choose(List<Card> hand, Contract contract, String chosen) {
        if (chosen == null) {
            return Optional.empty();
        }
        if (NOTHING.equals(chosen)) {
            return Optional.of(List.of());
        }
        List<String> named = List.of(chosen.split(","));
        Set<String> wanted = new HashSet<>(named);
        if (wanted.size() != named.size()) {
            return Optional.empty();
        }
        for (List<Declaration> option : options(hand, contract)) {
            List<Declaration> picked = option.stream()
                    .filter(declaration -> wanted.contains(key(List.of(declaration))))
                    .toList();
            if (picked.size() == wanted.size()) {
                return Optional.of(picked);
            }
        }
        return Optional.empty();
    }

    /**
     * Every way this hand may be announced, the richest first and announcing
     * nothing last. RULES §7, ANSWERED 18.
     *
     * <p>A card may serve a carré or a sequence, not both, and which is the
     * player's to say: four tens and 7 8 9 10 of spades is the four and a
     * terz of 7 8 9, or the quarte alone. And a player may keep what they
     * hold to themselves, so as not to tell the table their cards. A belote
     * is not among these: it is said, or not, with its own card.
     *
     * <p>A hand with nothing to announce has one option, nothing.
     */
    public static List<List<Declaration>> options(List<Card> hand, Contract contract) {
        if (contract == Contract.NO_TRUMPS) {
            return List.of(List.of());
        }
        List<Declaration> carres = carres(hand);

        // Every choice of fours, the sequences read again from what they
        // leave. At most two fours fit in eight cards, so at most four.
        List<List<Declaration>> found = new ArrayList<>();
        List<Integer> fours = new ArrayList<>();
        for (int taken = 0; taken < 1 << carres.size(); taken++) {
            List<Declaration> option = new ArrayList<>();
            Set<Card> spokenFor = new HashSet<>();
            for (int i = 0; i < carres.size(); i++) {
                if ((taken & 1 << i) != 0) {
                    option.add(carres.get(i));
                    spokenFor.addAll(carres.get(i).cards());
                }
            }
            int carresTaken = option.size();
            option.addAll(sequences(hand.stream().filter(card -> !spokenFor.contains(card)).toList()));
            if (!option.isEmpty() && found.stream().noneMatch(seen -> key(seen).equals(key(option)))) {
                found.add(option.stream().sorted(Comparator.comparingInt(Declaration::points).reversed()).toList());
                fours.add(carresTaken);
            }
        }

        // Richest first; between equals, the one with more fours, which are
        // the rarer thing to hold.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingInt(i -> found.get(i).stream().mapToInt(Declaration::points).sum())
                .thenComparingInt(fours::get)
                .reversed());

        List<List<Declaration>> options = new ArrayList<>();
        order.forEach(i -> options.add(found.get(i)));
        options.add(List.of());
        return List.copyOf(options);
    }

    /**
     * One way of announcing a hand, as a word a request can carry and a play
     * can keep: each declaration as kind, suit and top card, or
     * {@link #NOTHING}.
     */
    public static String key(List<Declaration> option) {
        if (option.isEmpty()) {
            return NOTHING;
        }
        return option.stream()
                .map(declaration -> declaration.kind() + "/"
                        + (declaration.suit() == null ? "-" : declaration.suit()) + "/" + declaration.topRank())
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * What this seat's declarations come to once these tricks have been played.
     * RULES §7, ANSWERED 16.
     *
     * <p>In all trumps a belote is said with whichever of its king and queen
     * reaches the table first, and only onto a trick led in its suit — its
     * own lead included. Thrown onto another suit's lead, the belote is not
     * said, and the other card cannot say it later. In a suit contract it
     * counts however its cards are played, cutting another suit's lead
     * included. Nothing but a belote is touched.
     *
     * @param held   the seat's declarations, from its dealt hand
     * @param seat   whose they are
     * @param tricks the tricks of the hand so far, the one on the table included
     */
    public static List<Declaration> standing(List<Declaration> held, Seat seat, List<Trick> tricks,
                                             Contract contract) {
        return standing(held, seat, tricks, contract, Set.of());
    }

    /**
     * As above, less any belote its player chose not to say: one whose first
     * king or queen was played with "без белот". A belote kept quiet is not
     * said later with the other card either.
     *
     * @param declined the suits whose belote this seat chose not to say
     */
    public static List<Declaration> standing(List<Declaration> held, Seat seat, List<Trick> tricks,
                                             Contract contract, Set<Suit> declined) {
        return held.stream()
                .filter(declaration -> declaration.kind() != DeclarationKind.BELOTE
                        || (!declined.contains(declaration.suit())
                            && (contract != Contract.ALL_TRUMPS || saidOnItsSuit(declaration.suit(), seat, tricks))))
                .toList();
    }

    /**
     * Whether the first of this seat's king and queen of the suit went onto a
     * trick led in that suit. True while neither has been played: the belote
     * can still be said.
     */
    private static boolean saidOnItsSuit(Suit suit, Seat seat, List<Trick> tricks) {
        for (Trick trick : tricks) {
            for (Play play : trick.plays()) {
                if (play.seat() == seat && play.card().suit() == suit
                        && (play.card().rank() == Rank.KING || play.card().rank() == Rank.QUEEN)) {
                    return trick.ledSuit().orElseThrow() == suit;
                }
            }
        }
        return true;
    }

    /** The longest run of three or more in each suit. */
    static List<Declaration> sequences(List<Card> hand) {
        List<Declaration> found = new ArrayList<>();
        Map<Suit, List<Rank>> bySuit = hand.stream()
                .collect(Collectors.groupingBy(Card::suit,
                        Collectors.mapping(Card::rank, Collectors.toList())));

        bySuit.forEach((suit, ranks) -> {
            List<Rank> sorted = ranks.stream().sorted(Comparator.comparingInt(Rank::naturalOrder)).toList();
            int runStart = 0;
            for (int i = 1; i <= sorted.size(); i++) {
                boolean consecutive = i < sorted.size()
                        && sorted.get(i).naturalOrder() == sorted.get(i - 1).naturalOrder() + 1;
                if (!consecutive) {
                    int length = i - runStart;
                    if (length >= SHORTEST_SEQUENCE) {
                        found.add(sequence(suit, sorted.get(i - 1), length));
                    }
                    runStart = i;
                }
            }
        });
        return found;
    }

    private static Declaration sequence(Suit suit, Rank top, int length) {
        DeclarationKind kind = switch (length) {
            case 3 -> DeclarationKind.TERZ;
            case 4 -> DeclarationKind.QUARTE;
            // OPEN 14 — six, seven and eight in a row are scored as a quinte
            // here. The rules page stops at five.
            default -> DeclarationKind.QUINTE;
        };
        return new Declaration(kind, suit, top, kind.points());
    }

    /** Four of a kind, where the rank is worth anything. */
    static List<Declaration> carres(List<Card> hand) {
        return hand.stream()
                .collect(Collectors.groupingBy(Card::rank, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() == 4)
                .map(entry -> new Declaration(DeclarationKind.CARRE, null, entry.getKey(), carrePoints(entry.getKey())))
                .filter(declaration -> declaration.points() > 0)
                .sorted(Comparator.comparingInt(declaration -> -declaration.points()))
                .toList();
    }

    private static int carrePoints(Rank rank) {
        return switch (rank) {
            case JACK -> 200;
            case NINE -> 150;
            case ACE, TEN, KING, QUEEN -> 100;
            // Four sevens or four eights are worth saying nothing about.
            case EIGHT, SEVEN -> 0;
        };
    }

    /**
     * King and queen together in a trump suit.
     *
     * <p>OPEN 15 — in all trumps every suit is a trump suit, so a belote is
     * counted in each suit that holds both. If the answer is that all trumps has
     * no belote at all, or only one, this is the method that changes.
     */
    static List<Declaration> belotes(List<Card> hand, Contract contract) {
        return java.util.Arrays.stream(Suit.values())
                .filter(contract::isTrump)
                .filter(suit -> hand.contains(new Card(suit, Rank.KING))
                        && hand.contains(new Card(suit, Rank.QUEEN)))
                .map(suit -> new Declaration(DeclarationKind.BELOTE, suit, Rank.KING, DeclarationKind.BELOTE.points()))
                .toList();
    }
}
