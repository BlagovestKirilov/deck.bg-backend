package bg.deck.santase.model;

import bg.deck.common.model.TurnClock;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/** The deal being played: the deck, the trump, whose turn it is and until when. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "santase", name = "game_state")
public class SantaseGameState extends BaseEntity implements TurnClock {

    /**
     * Santase's budget for one turn: 20s to act, then a 10s "still there?"
     * warning, plus 3s of slack so the client always reaches the warning before
     * this deadline does.
     */
    public static final int TURN_SECONDS = 33;

    /**
     * The deck in the order it is drawn from: the first card is the next one
     * drawn, the last is the trump. The order is stored — the rows' own order
     * is not one.
     */
    @ElementCollection
    @CollectionTable(schema = "santase", name = "deck", joinColumns = @JoinColumn(name = "state_id"))
    @OrderColumn(name = "card_index")
    private List<Card> deck;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "trump_card_id"))
    @AttributeOverride(name = "suit", column = @Column(name = "trump_card_suit"))
    @AttributeOverride(name = "rank", column = @Column(name = "trump_card_rank"))
    private Card trumpCard;

    @ManyToOne
    private SantaseSeat firstTurnPlayer;

    @ManyToOne
    private SantaseSeat inTurnPlayer;

    @ManyToOne
    private SantaseSeat closedByPlayer;

    private Instant nextMoveTime;

    public boolean isClosed() {
        return this.closedByPlayer != null;
    }

    /** Hands the turn to {@code inTurnPlayer} and restarts the clock. */
    public void setInTurnPlayer(SantaseSeat inTurnPlayer) {
        this.inTurnPlayer = inTurnPlayer;
        this.extendNextMoveTime();
    }

    @Override
    public int turnSeconds() {
        return TURN_SECONDS;
    }

    @Override
    public void extendNextMoveTime() {
        this.nextMoveTime = Instant.now().plusSeconds(turnSeconds());
    }

    public boolean isInTurn(SantaseSeat player) {
        return player.equals(this.inTurnPlayer);
    }
}
