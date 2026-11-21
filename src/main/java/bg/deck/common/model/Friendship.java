package bg.deck.common.model;

import bg.deck.common.enums.FriendshipStatus;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Two accounts and the friendship between them: asked for by the requester,
 * pending until the addressee accepts it. One row per pair, whichever way it
 * was asked; deleting either account deletes it.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "friendship")
public class Friendship extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    /** When it was last answered or ended; null while it is pending. */
    @Column(name = "responded_at")
    private Instant respondedAt;

    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = FriendshipStatus.PENDING;
    }

    /** Asked afresh, by this requester of this addressee: the row reused. */
    public void askAgain(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = FriendshipStatus.PENDING;
        this.respondedAt = null;
    }

    /** Ends it as this, now. */
    public void end(FriendshipStatus how) {
        this.status = how;
        this.respondedAt = Instant.now();
    }

    /** The other account in it, seen from this player's side. */
    public User otherThan(String username) {
        return requester.getUsername().equals(username) ? addressee : requester;
    }
}
