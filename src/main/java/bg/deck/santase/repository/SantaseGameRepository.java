package bg.deck.santase.repository;

import bg.deck.santase.model.SantaseGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Santase games. Owned by SantaseTableService, and spoken to by nothing else. */
public interface SantaseGameRepository extends JpaRepository<SantaseGame, UUID> {

    /** The games this player is still in, newest first. There is never more than one. */
    @Query("""
                 SELECT game FROM SantaseGame game
                 WHERE (game.firstPlayer.username = :username
                    OR game.secondPlayer.username = :username)
                 AND game.winner IS NULL
                 ORDER BY game.createdAt DESC
            """)
    List<SantaseGame> findActiveGamesByUsername(@Param("username") String username);

    /**
     * The id of the game this player is still in, newest first — the id alone,
     * for the question asked each time a game screen opens, so the seats and
     * the state are not loaded just to be thrown away.
     */
    @Query("""
                 SELECT game.id FROM SantaseGame game
                 WHERE (game.firstPlayer.username = :username
                    OR game.secondPlayer.username = :username)
                 AND game.winner IS NULL
                 ORDER BY game.createdAt DESC
            """)
    List<UUID> findActiveGameIdsByUsername(@Param("username") String username);

    /** Every game still being played: what the turn timer re-arms after a restart. */
    @Query("SELECT game FROM SantaseGame game WHERE game.winner IS NULL")
    List<SantaseGame> findAllActive();
}
