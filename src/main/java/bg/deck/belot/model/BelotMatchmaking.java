package bg.deck.belot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row, and its only job is to be locked.
 *
 * <p>Sitting a player down is read-then-write: look for a table with room,
 * and open one if there is none. Four people pressing Търси at the same
 * moment all read "no table with room" and all open one, which is how four
 * players ended up at three tables.
 *
 * <p>There is nothing else to lock. Locking the table you found does not help
 * when the race is about a table that does not exist yet, and a unique index
 * on "only one table may be waiting" is a Postgres partial index that H2 does
 * not have, so the tests would stop testing the fix. A row that always exists
 * can be locked before the read, on either database, with plain
 * {@code SELECT ... FOR UPDATE}.
 *
 * <p>Held for the length of the transaction and released on commit, so the
 * next player in reads what the last one wrote. It serialises matchmaking and
 * nothing else — a game in progress never touches this row.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "belot", name = "matchmaking")
public class BelotMatchmaking {

    /** The only row there is. */
    public static final int ROW = 1;

    @Id
    @Column(nullable = false)
    private Integer id;

    public BelotMatchmaking(Integer id) {
        this.id = id;
    }
}
