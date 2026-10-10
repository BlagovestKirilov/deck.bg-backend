package bg.deck.tabla.model;

import bg.deck.common.exception.UserNotPartOfGameException;
import bg.deck.common.model.TurnClock;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
 * A game of табла: two seats, the board, the committed dice seed, and who won.
 *
 * <p>In tabla's own schema, with nothing pointing outside it. Its id is kept
 * as it was when the game moved here: the dice are worked out from it, and the
 * players' screens subscribe by it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "tabla", name = "game")
public class TablaGame extends BaseEntity {

    @ManyToOne
    private TablaSeat firstPlayer;

    @ManyToOne
    private TablaSeat secondPlayer;

    @OneToOne(cascade = CascadeType.ALL)
    private TablaGameState state;

    /** Committed dice seed; revealed only once the game is finished. */
    private byte[] serverSeed;

    @Column(length = 64)
    private String serverSeedHash;

    @ManyToOne
    private TablaSeat winner;

    @ManyToOne
    private TablaSeat surrenderPlayer;

    private Instant finishedAt;

    public TablaSeat getPlayerByUsername(String username) {
        if (username.equals(firstPlayer.getUsername())) {
            return firstPlayer;
        } else if (username.equals(secondPlayer.getUsername())) {
            return secondPlayer;
        } else {
            throw new UserNotPartOfGameException(username);
        }
    }

    public TablaSeat getOpponentPlayerByUsername(String username) {
        if (username.equals(firstPlayer.getUsername())) {
            return secondPlayer;
        } else if (username.equals(secondPlayer.getUsername())) {
            return firstPlayer;
        } else {
            throw new UserNotPartOfGameException(username);
        }
    }

    public TablaSeat getOpponent(TablaSeat player) {
        if (player.equals(firstPlayer)) {
            return secondPlayer;
        } else if (player.equals(secondPlayer)) {
            return firstPlayer;
        } else {
            throw new UserNotPartOfGameException(player.getUsername());
        }
    }

    /** The board's clock, which is what the turn timer reads. */
    public TurnClock getTurnClock() {
        return state;
    }

    /** Marks the game won. Writing the result into the players' records is TablaStatsService's. */
    public void setWinner(TablaSeat winnerPlayer, boolean opponentSurrendered) {
        this.winner = winnerPlayer;
        this.finishedAt = Instant.now();
        if (opponentSurrendered) {
            this.surrenderPlayer = getOpponent(winnerPlayer);
        }
    }
}
