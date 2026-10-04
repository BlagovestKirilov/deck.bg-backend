package bg.deck.belot.model;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.BidKind;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.Seat;
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
 * One turn of the bidding, as it was said.
 *
 * <p>The row is the turn, not the state it produced: the state is what you get
 * by replaying the turns in order, which is one thing to keep right instead of
 * two that can disagree.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "bid")
public class BelotBid extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "deal_id", nullable = false)
    private BelotDeal deal;

    /** Its place in the bidding, counted from zero. */
    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BidKind kind;

    /** Named on a bid, null on a pass, a contra or a recontra. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Contract contract;

    public BelotBid(int ordinal, BidAction action) {
        this.ordinal = ordinal;
        this.seat = action.seat();
        this.kind = action.kind();
        this.contract = action.contract();
    }

    public BidAction action() {
        return new BidAction(seat, kind, contract);
    }
}
