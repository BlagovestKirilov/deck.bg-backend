package bg.deck.common.model;

import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import bg.deck.santase.model.Card;

@Setter
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Player extends BaseEntity {
    /**
     * A player row is one seat in one game, not one row per user: result, score,
     * hand and playedCard are per-game mutable state. The unique constraint on
     * user_id was already dropped in changeset 007.
     */
    @ManyToOne
    private User user;

    private Integer result;

    private Integer score;

    @Transient
    private Integer bonus;

    @ElementCollection
    @CollectionTable(name = "player_hand", joinColumns = @JoinColumn(name = "player_id"))
    private List<Card> hand;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "played_card_id"))
    @AttributeOverride(name = "suit", column = @Column(name = "played_card_suit"))
    @AttributeOverride(name = "rank", column = @Column(name = "played_card_rank"))
    private Card playedCard;

    private Boolean isBlanked;

    private Integer inactivityCount;

    @OneToOne
    private DeletedUser deletedUser;

    public String getUsername() {
        if (user != null) {
            return user.getUsername();
        }
        return deletedUser != null ? deletedUser.getUsername() : null;
    }

    public void drawCard(Card lastDrawnCard) {
        hand.forEach(card -> card.setIsLastDrawn(false));
        lastDrawnCard.setIsLastDrawn(true);
        hand.add(lastDrawnCard);
    }
}
