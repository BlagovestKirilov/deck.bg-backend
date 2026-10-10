package bg.deck.belot.model;

import bg.deck.belot.enums.BelotDealStatus;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.engine.Card;
import bg.deck.belot.enums.DealResult;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.engine.Play;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.engine.TrickResolver;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.enums.Doubling;
import bg.deck.belot.enums.Seat;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One hand of belot: its number, who dealt it, and what was said over it.
 *
 * <p>The cards are not here. {@code dealNumber} and the game's seed reproduce
 * the shuffle, so a hand is derived whenever it is needed and cannot drift from
 * what the table was promised before the first card.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "deal")
public class BelotDeal extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private BelotGame game;

    /** Counted from one, and the number the shuffle is derived from. */
    @Column(name = "deal_number", nullable = false)
    private int dealNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "dealer_seat", nullable = false, length = 10)
    private Seat dealerSeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BelotDealStatus status = BelotDealStatus.BIDDING;

    /** What was bid, once the bidding is over. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "declarer_seat", length = 10)
    private Seat declarerSeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Doubling doubling = Doubling.NONE;

    /**
     * What the deal came to, once it is played out: the card points each side
     * took, the game points written down for them, and which of the three
     * things happened. Null until then.
     *
     * <p>Kept rather than recomputed. A score sheet that works itself out
     * again on every read is a score sheet that can change after the fact.
     */
    @Column(name = "caller_points")
    private Integer callerPoints;

    @Column(name = "opponent_points")
    private Integer opponentPoints;

    @Column(name = "caller_score")
    private Integer callerScore;

    @Column(name = "opponent_score")
    private Integer opponentScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DealResult result;

    /**
     * When the turn now on the table started.
     *
     * <p>Set whenever a turn begins — the deal being dealt, a bid taken, a
     * card played — and read by two: the scheduler that acts for a player
     * who has run out, and the client that counts down. One clock, so the
     * one the player watches is the one they are judged by.
     */
    @Column(name = "turn_started_at")
    private Instant turnStartedAt;

    /**
     * Where the deck was cut, counted from the top; null while it is waiting
     * to be cut, when nobody may bid. Purely the picture of a cut — the hand
     * was dealt from the seed before it — and 0 for a deal made without one.
     */
    @Column(name = "cut_at")
    private Integer cutAt = 0;

    @OrderBy("ordinal ASC")
    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BelotBid> bids = new ArrayList<>();

    @OrderBy("trickNo ASC, orderInTrick ASC")
    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BelotPlay> plays = new ArrayList<>();

    public BelotDeal(BelotGame game, int dealNumber, Seat dealerSeat) {
        this.game = game;
        this.dealNumber = dealNumber;
        this.dealerSeat = dealerSeat;
    }

    /**
     * The bidding as it stands, replayed from the turns that were taken.
     *
     * <p>Rebuilt rather than stored: {@link Bidding} already knows every rule
     * about whose turn it is and what beats what, and a second copy of that
     * knowledge in columns is a second copy to get wrong.
     */
    public Bidding bidding() {
        Bidding bidding = Bidding.startedBy(dealerSeat);
        for (BelotBid bid : bids) {
            bidding = bidding.apply(bid.action());
        }
        return bidding;
    }

    public void add(BelotBid bid) {
        bid.setDeal(this);
        bids.add(bid);
    }

    public int nextOrdinal() {
        return bids.size();
    }

    public void add(BelotPlay play) {
        play.setDeal(this);
        plays.add(play);
    }

    /**
     * Every trick so far, the last one possibly unfinished.
     *
     * <p>Built from the cards rather than kept beside them: a trick is four
     * plays in order, and there is nothing else to it worth storing.
     */
    public List<Trick> tricks() {
        List<Trick> tricks = new ArrayList<>();
        Trick current = Trick.empty();
        int number = 1;

        for (BelotPlay played : plays) {
            if (played.getTrickNo() != number) {
                tricks.add(current);
                current = Trick.empty();
                number = played.getTrickNo();
            }
            current = current.with(played.play());
        }
        if (!current.isEmpty()) {
            tricks.add(current);
        }
        return List.copyOf(tricks);
    }

    /** The trick being played; empty when the next card starts a new one. */
    public Trick currentTrick() {
        List<Trick> tricks = tricks();
        if (tricks.isEmpty()) {
            return Trick.empty();
        }
        Trick last = tricks.getLast();
        return last.plays().size() < Seat.values().length ? last : Trick.empty();
    }

    /** Which trick the next card belongs to, counted from one. */
    public int currentTrickNumber() {
        return plays.size() / Seat.values().length + 1;
    }

    /** Where in the trick the next card goes: 0 is the lead. */
    public int nextPlaceInTrick() {
        return plays.size() % Seat.values().length;
    }

    public boolean isPlayedOut() {
        return plays.size() == Dealing.HAND_SIZE * Seat.values().length;
    }

    /** The cards this seat has already put on the table. */
    public List<Card> playedBy(Seat seat) {
        return plays.stream()
                .filter(play -> play.getSeat() == seat)
                .map(BelotPlay::card)
                .toList();
    }

    /**
     * Who took the last finished trick, and so leads the next one.
     *
     * <p>Empty while a trick is in progress, and empty before the first card
     * — the lead then belongs to the seat on the dealer’s right, which is the
     * deal’s business rather than a trick’s.
     */
    public Optional<Seat> lastTrickWinner() {
        List<Trick> tricks = tricks();
        if (tricks.isEmpty()) {
            return Optional.empty();
        }
        Trick last = tricks.getLast();
        return last.plays().size() == Seat.values().length
                ? TrickResolver.winning(last, contract).map(Play::seat)
                : Optional.empty();
    }

    /** Waiting for the player on the dealer's left to cut, before any bid. */
    public boolean isAwaitingCut() {
        return status == BelotDealStatus.BIDDING && cutAt == null;
    }

    /** The player who cuts: the one on the dealer's left, who plays just before him. */
    public Seat cutter() {
        Seat[] seats = Seat.values();
        return seats[(dealerSeat.ordinal() + seats.length - 1) % seats.length];
    }
}
