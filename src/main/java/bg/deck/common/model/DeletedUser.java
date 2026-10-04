package bg.deck.common.model;

import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * What is left of an account after it is deleted: a name for the seat.
 *
 * <p>Finished games still have to say who sat in them, so the username stays
 * and the row with it. Nothing else does: no email, no password hash, no IP
 * address.
 *
 * <p>Extends {@link BaseEntity}, not {@code BaseUser}. Inheriting the user's
 * shape was what made deletion copy the whole account — every field the two
 * classes shared came across, and stayed for good. A tombstone is not a user,
 * and now cannot hold what a user holds. Changeset 016 drops the columns those
 * fields used to occupy.
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deleted_users")
public class DeletedUser extends BaseEntity {
    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant deletedAt;
}
