package bg.deck.belot.model;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Play;
import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Suit;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One card, on the table, in the place it was put.
 *
 * <p>Which trick and where in it, rather than a timestamp: the order of a
 * trick is what decides who won it, and an ordering that depends on clocks is
 * an ordering that can surprise you.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "play")
public class BelotPlay extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "deal_id", nullable = false)
    private BelotDeal deal;

    /** Counted from one, as players count tricks. */
    @Column(name = "trick_no", nullable = false)
    private int trickNo;

    /** Where in the trick: 0 is the lead. */
    @Column(name = "order_in_trick", nullable = false)
    private int orderInTrick;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Suit suit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rank rank;

    /**
     * On a player's first card of the hand: which way of announcing their hand
     * they chose (Declarations.key), or NONE. Null — every play from before
     * players could choose — means the richest.
     */
    @Column(length = 200)
    private String announced;

    /**
     * On the first king or queen of a belote: false when it was played without
     * saying it. Null means said, as every belote was before this.
     */
    @Column
    private Boolean belote;

    public BelotPlay(int trickNo, int orderInTrick, Seat seat, Card card) {
        this.trickNo = trickNo;
        this.orderInTrick = orderInTrick;
        this.seat = seat;
        this.suit = card.suit();
        this.rank = card.rank();
    }

    public Card card() {
        return new Card(suit, rank);
    }

    public Play play() {
        return new Play(seat, card());
    }
}
