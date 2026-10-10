package bg.deck.tabla.model;

import bg.deck.common.model.base.BaseEntity;
import bg.deck.tabla.engine.BoardState;
import bg.deck.tabla.engine.Dice;
import bg.deck.tabla.engine.Hop;
import bg.deck.tabla.enums.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import bg.deck.common.model.TurnClock;

/**
 * Live state of a табла game.
 *
 * <p>The board is one {@code varchar} rather than an {@code @ElementCollection}:
 * Hibernate rewrites an entire element collection (delete-all + insert-all) on
 * any mutation, and the board changes on every single checker hop.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "tabla", name = "game_state")
public class TablaGameState extends BaseEntity implements TurnClock {

    /**
     * Табла's budget for one turn: 45s to act plus a 10s warning and 3s of
     * slack. A turn here is several taps (roll, move each die, confirm), not
     * one card, so santase's 20s is too short.
     */
    public static final int TURN_SECONDS = 58;

    /** Guards against a double-tapped move being applied twice. */
    @Version
    private int version;

    @ManyToOne
    private TablaSeat firstTurnPlayer;

    @ManyToOne
    private TablaSeat inTurnPlayer;

    private Instant nextMoveTime;

    @Column(length = 160, nullable = false)
    private String board;

    /** Board as it stood at the start of this turn — the undo anchor. */
    @Column(length = 160)
    private String turnStartBoard;

    private Integer die1;
    private Integer die2;

    /** Dice still unplayed this turn, e.g. "5,5,5". */
    @Column(length = 32)
    private String remainingDice;

    /** Hops applied so far this turn, for undo and for client highlighting. */
    @Column(length = 256)
    private String pendingHops;

    /** {@code M} — how many dice the roll allows, computed once at roll time. */
    private int maxDiceUsable;

    /** Monotonic counter used to derive dice from the committed seed. */
    private int turnIndex;

    /* ---------------- board ---------------- */

    public BoardState boardState() {
        return BoardState.decode(board);
    }

    public void setBoardState(BoardState state) {
        this.board = state.encode();
    }

    public BoardState turnStartBoardState() {
        return BoardState.decode(turnStartBoard);
    }

    public void snapshotTurnStart() {
        this.turnStartBoard = this.board;
    }

    /* ---------------- dice ---------------- */

    public boolean isRolled() {
        return die1 != null && die2 != null;
    }

    public int[] remainingDiceValues() {
        return Dice.decode(remainingDice);
    }

    public void setRemainingDiceValues(int[] dice) {
        this.remainingDice = Dice.encode(dice);
    }

    public void clearTurn() {
        this.die1 = null;
        this.die2 = null;
        this.remainingDice = null;
        this.pendingHops = null;
        this.turnStartBoard = null;
        this.maxDiceUsable = 0;
    }

    /* ---------------- pending hops ---------------- */

    public List<Hop> pendingHopList() {
        if (pendingHops == null || pendingHops.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(pendingHops.split(";")).map(Hop::decode).collect(java.util.stream.Collectors.toList());
    }

    public void setPendingHopList(List<Hop> hops) {
        this.pendingHops = hops.isEmpty() ? null
                : hops.stream().map(Hop::encode).reduce((a, b) -> a + ";" + b).orElse(null);
    }

    public int usedDiceCount() {
        return pendingHopList().size();
    }

    /* ---------------- sides ---------------- */

    /**
     * The first player is always WHITE, so no side column is stored. Which player
     * moves first is decided by the opening roll and lives in {@code inTurnPlayer}.
     */
    public Side sideOf(TablaGame game, TablaSeat player) {
        return game.getFirstPlayer().equals(player) ? Side.WHITE : Side.BLACK;
    }

    /* ---------------- TurnClock ---------------- */

    /** Hands the turn to {@code inTurnPlayer} and restarts the clock. */
    public void setInTurnPlayer(TablaSeat inTurnPlayer) {
        this.inTurnPlayer = inTurnPlayer;
        extendNextMoveTime();
    }

    @Override
    public int turnSeconds() {
        return TURN_SECONDS;
    }

    @Override
    public void extendNextMoveTime() {
        this.nextMoveTime = Instant.now().plusSeconds(turnSeconds());
    }

    public boolean isInTurn(TablaSeat player) {
        return player.equals(this.inTurnPlayer);
    }
}
