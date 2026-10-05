package bg.deck.belot.model;

import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A table of belot: its four seats, its score sheet, and the seed its deals are
 * shuffled from.
 *
 * <p>The deck is never stored. Every deal is dealt again from {@code serverSeed}
 * and the deal's number, so the shuffle is reproducible and, once the seed is
 * revealed at the end, checkable by the players — the hash of it is shown
 * before a card is dealt. Табла's dice work the same way.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "game")
public class BelotGame extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BelotGameStatus status = BelotGameStatus.WAITING;

    /** Whose deal it is. Null until the table is full. */
    @Enumerated(EnumType.STRING)
    @Column(name = "dealer_seat", length = 10)
    private Seat dealerSeat;

    @Column(name = "north_south_score", nullable = false)
    private int northSouthScore;

    @Column(name = "east_west_score", nullable = false)
    private int eastWestScore;

    /** Points from a level deal, waiting for whoever wins the next one. */
    @Column(name = "hanging_points", nullable = false)
    private int hangingPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "winner_team", length = 20)
    private Team winnerTeam;

    /** How the game ended early, if it did: given up, or left to run out. */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private BelotForfeit forfeit;

    /** Whose doing the forfeit was. Null for a game played to its end. */
    @Column(name = "forfeited_by", length = 20)
    private String forfeitedBy;

    @Column(name = "server_seed", nullable = false)
    private byte[] serverSeed;

    @Column(name = "server_seed_hash", nullable = false, length = 64)
    private String serverSeedHash;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BelotSeat> seats = new ArrayList<>();

    public boolean isFull() {
        return seats.size() == Seat.values().length;
    }

    public Optional<BelotSeat> seatOf(String username) {
        return seats.stream().filter(seat -> seat.getUsername().equals(username)).findFirst();
    }

    /** The seats nobody has taken yet, in the order they are handed out. */
    public List<Seat> freeSeats() {
        List<Seat> taken = seats.stream().map(BelotSeat::getSeat).toList();
        return java.util.Arrays.stream(Seat.values()).filter(seat -> !taken.contains(seat)).toList();
    }

    public void add(BelotSeat seat) {
        seat.setGame(this);
        seats.add(seat);
    }

    public void addScore(Team team, int points) {
        if (team == Team.NORTH_SOUTH) {
            northSouthScore += points;
        } else {
            eastWestScore += points;
        }
    }
}
