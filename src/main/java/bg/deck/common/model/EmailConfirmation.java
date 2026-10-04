package bg.deck.common.model;

import bg.deck.common.enums.EmailConfirmationStatus;
import bg.deck.common.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Setter
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class EmailConfirmation extends BaseEntity {
    @ManyToOne
    private User user;

    @Column(nullable = false)
    private UUID confirmationToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailConfirmationStatus status;

    @ManyToOne
    private DeletedUser deletedUser;

    public EmailConfirmation(User user) {
        this.user = user;
        this.confirmationToken = UUID.randomUUID();
        this.status = EmailConfirmationStatus.PENDING;
    }
}
