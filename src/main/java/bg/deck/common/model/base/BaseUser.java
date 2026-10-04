package bg.deck.common.model.base;

import bg.deck.common.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public abstract class BaseUser extends BaseEntity {
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String ipAddress;

    /*
     * A player's record — wins, losses, Elo and rank — lives in
     * {@link bg.deck.common.model.UserGameStats}, one row per game.
     * The single santaseWins/santaseLosses/rank/rankRating set that used to sit
     * here could only ever describe one game; changeset 015 drops the columns.
     */

    @Column(nullable = false)
    private Boolean isEmailConfirmed;
}
