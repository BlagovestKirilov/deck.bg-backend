package bg.deck.common.model;

import bg.deck.common.enums.GameType;
import bg.deck.common.enums.Scope;
import bg.deck.common.model.base.BaseUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Setter
@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseUser {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * How far this account can see: {@link Scope#PUBLIC} for almost everyone,
     * higher for the few who are meant to reach a game before the rest.
     * Initialised here as well as defaulted in the column, or Hibernate writes
     * an explicit null on insert and the constraint refuses it.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope = Scope.PUBLIC;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserGameStats> stats = new LinkedHashSet<>();

    /**
     * This user's record for one game. Rows are created eagerly at registration
     * and backfilled for pre-existing users, so a missing row means the data is
     * genuinely inconsistent rather than merely not created yet.
     */
    public UserGameStats statsFor(GameType gameType) {
        return stats.stream()
                .filter(s -> s.getGameType() == gameType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no " + gameType + " stats row for user " + getUsername()));
    }

    public void addStats(UserGameStats gameStats) {
        this.stats.add(gameStats);
    }
}
