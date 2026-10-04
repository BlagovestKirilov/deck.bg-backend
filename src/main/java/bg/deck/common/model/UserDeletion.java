package bg.deck.common.model;

import bg.deck.common.enums.UserDeletionStatus;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Setter
@Getter
@ToString
@NoArgsConstructor
@Entity
public class UserDeletion extends BaseEntity {
    @ManyToOne
    private User user;

    @Column(nullable = false)
    private UUID userDeletionToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserDeletionStatus status;

    @ManyToOne
    private DeletedUser deletedUser;

    public UserDeletion(User user) {
        this.user = user;
        this.userDeletionToken = UUID.randomUUID();
        this.status = UserDeletionStatus.PENDING;
    }
}
