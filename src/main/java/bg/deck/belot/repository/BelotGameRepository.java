package bg.deck.belot.repository;

import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BelotGameRepository extends JpaRepository<BelotGame, UUID> {

    /** Tables still short of players, oldest first, so nobody waits twice. */
    List<BelotGame> findByStatusOrderByCreatedAtAsc(BelotGameStatus status);

    /** Every table this player has ever sat at, finished ones included. */
    @Query("""
            select game from BelotGame game
              join game.seats seat
             where seat.username = :username
            """)
    List<BelotGame> findGamesOf(String username);

    /** The table this player is at, if they are at one. */
    @Query("""
            select game from BelotGame game
              join game.seats seat
             where seat.username = :username
               and game.status <> bg.deck.belot.model.BelotGameStatus.FINISHED
            """)
    Optional<BelotGame> findUnfinishedGameOf(String username);
}
