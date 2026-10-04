package bg.deck.santase.model;

import bg.deck.santase.enums.Rank;
import bg.deck.santase.enums.Suit;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class Card {
    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Suit suit;

    @Enumerated(EnumType.STRING)
    private Rank rank;

    private Boolean isPlayable;

    private Boolean isLastDrawn;

    public int getPoints() {
        return rank.getPoints();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Card card)) return false;
        return Objects.equals(id, card.id) && suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, suit, rank);
    }
}
