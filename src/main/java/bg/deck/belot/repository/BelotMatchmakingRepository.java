package bg.deck.belot.repository;

import bg.deck.belot.model.BelotMatchmaking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BelotMatchmakingRepository extends JpaRepository<BelotMatchmaking, Integer> {

    /**
     * Takes the matchmaking lock, waiting for whoever holds it.
     *
     * <p>{@code SELECT ... FOR UPDATE}, which Postgres and H2 both understand,
     * so the concurrency test exercises the same mechanism production uses.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from BelotMatchmaking row where row.id = 1")
    Optional<BelotMatchmaking> lock();
}
