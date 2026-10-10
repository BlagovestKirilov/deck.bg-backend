package bg.deck.common.model;

import bg.deck.common.enums.GameType;
import bg.deck.common.exception.UserNotPartOfGameException;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import bg.deck.santase.model.GameState;
import bg.deck.tabla.model.TablaGameState;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Game extends BaseEntity {

    @ManyToOne
    private Player firstPlayer;

    @ManyToOne
    private Player secondPlayer;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 20)
    @Builder.Default
    private GameType gameType = GameType.SANTASE;

    @OneToOne(cascade = CascadeType.ALL)
    private GameState state;

    /**
     * Table state for табла. Exactly one of {@code state} / {@code tablaState} is
     * set, chosen by {@link #gameType}. Two nullable links rather than a JOINED
     * hierarchy: an inheritance split would force a discriminator onto the live
     * game_state table and touch every line of the working Santase service.
     */
    @OneToOne(cascade = CascadeType.ALL)
    private TablaGameState tablaState;

    /** Committed dice seed; revealed only once the game is finished. */
    private byte[] serverSeed;

    @Column(length = 64)
    private String serverSeedHash;

    @ManyToOne
    private Player winner;

    @ManyToOne
    private Player surrenderPlayer;

    private Instant finishedAt;

    public Player getPlayerByUsername(String username) {
        if (username.equals(firstPlayer.getUsername())) {
            return firstPlayer;
        } else if (username.equals(secondPlayer.getUsername())) {
            return secondPlayer;
        } else {
            throw new UserNotPartOfGameException(username);
        }
    }

    public Player getOpponentPlayerByUsername(String username) {
        if (username.equals(firstPlayer.getUsername())) {
            return secondPlayer;
        } else if (username.equals(secondPlayer.getUsername())) {
            return firstPlayer;
        } else {
            throw new UserNotPartOfGameException(username);
        }
    }

    public Player getOpponent(Player player) {
        if (player.equals(firstPlayer)) {
            return secondPlayer;
        } else if (player.equals(secondPlayer)) {
            return firstPlayer;
        } else {
            throw new UserNotPartOfGameException(player.getUsername());
        }
    }

    /** Whichever state object drives the turn timer for this game type. */
    public TurnClock getTurnClock() {
        return state != null ? state : tablaState;
    }

    public void setWinner(Player winnerPlayer, boolean opponentSurrendered) {
        this.winner = winnerPlayer;
        this.finishedAt = Instant.now();

        Player opponent = getOpponent(winnerPlayer);
        if (opponentSurrendered) {
            this.surrenderPlayer = opponent;
        }

        // The records are each game's own, in its own schema: the game that
        // finished writes them (SantaseStatsService, TablaStatsService).
    }
}
