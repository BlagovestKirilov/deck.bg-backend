package bg.deck.santase.model;

import bg.deck.common.exception.UserNotPartOfGameException;
import bg.deck.common.model.TurnClock;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A game of сантасе: two seats, the deal being played, and who won.
 *
 * <p>In santase's own schema, with nothing pointing outside it. Its id is the
 * one the players' screens subscribe by, and it is kept as it was when the
 * game moved here.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "santase", name = "game")
public class SantaseGame extends BaseEntity {

    @ManyToOne
    private SantaseSeat firstPlayer;

    @ManyToOne
    private SantaseSeat secondPlayer;

    @OneToOne(cascade = CascadeType.ALL)
    private SantaseGameState state;

    @ManyToOne
    private SantaseSeat winner;

    @ManyToOne
    private SantaseSeat surrenderPlayer;

    private Instant finishedAt;

    public SantaseSeat getPlayerByUsername(String username) {
        if (username.equals(firstPlayer.getUsername())) {
            return firstPlayer;
        } else if (username.equals(secondPlayer.getUsername())) {
            return secondPlayer;
        } else {
            throw new UserNotPartOfGameException(username);
        }
    }

    public SantaseSeat getOpponentPlayerByUsername(String username) {
        if (username.equals(firstPlayer.getUsername())) {
            return secondPlayer;
        } else if (username.equals(secondPlayer.getUsername())) {
            return firstPlayer;
        } else {
            throw new UserNotPartOfGameException(username);
        }
    }

    public SantaseSeat getOpponent(SantaseSeat player) {
        if (player.equals(firstPlayer)) {
            return secondPlayer;
        } else if (player.equals(secondPlayer)) {
            return firstPlayer;
        } else {
            throw new UserNotPartOfGameException(player.getUsername());
        }
    }

    /** The deal's clock, which is what the turn timer reads. */
    public TurnClock getTurnClock() {
        return state;
    }

    /** Marks the game won. Writing the result into the players' records is SantaseStatsService's. */
    public void setWinner(SantaseSeat winnerPlayer, boolean opponentSurrendered) {
        this.winner = winnerPlayer;
        this.finishedAt = Instant.now();
        if (opponentSurrendered) {
            this.surrenderPlayer = getOpponent(winnerPlayer);
        }
    }
}
