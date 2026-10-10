package bg.deck.santase.model;

import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * One player's seat in one сантасе game: their hand, the card they have
 * played, the deal's score and the game's result.
 *
 * <p>Named by username, in santase's own schema, so a seat points at nothing
 * outside it. A deleted account's seats are renamed to the tombstone name,
 * which no account can take.
 */
@Setter
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "santase", name = "seat")
public class SantaseSeat extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String username;

    private Integer result;

    private Integer score;

    @Transient
    private Integer bonus;

    /**
     * The hand in the order it was dealt and drawn. The order is stored: the
     * order of the rows is not, and a hand read back in another order is a
     * different hand on the screen.
     */
    @ElementCollection
    @CollectionTable(schema = "santase", name = "hand", joinColumns = @JoinColumn(name = "seat_id"))
    @OrderColumn(name = "card_index")
    private List<Card> hand;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "played_card_id"))
    @AttributeOverride(name = "suit", column = @Column(name = "played_card_suit"))
    @AttributeOverride(name = "rank", column = @Column(name = "played_card_rank"))
    private Card playedCard;

    private Boolean isBlanked;

    private Integer inactivityCount;

    public void drawCard(Card lastDrawnCard) {
        hand.forEach(card -> card.setIsLastDrawn(false));
        lastDrawnCard.setIsLastDrawn(true);
        hand.add(lastDrawnCard);
    }
}
