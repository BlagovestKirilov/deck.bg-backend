package bg.deck.tabla.repository;

import bg.deck.tabla.model.TablaGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Табла games. Owned by TablaUtilService, and spoken to by nothing else. */
public interface TablaGameRepository extends JpaRepository<TablaGame, UUID> {

    /** The games this player is still in, newest first. There is never more than one. */
    @Query("""
                 SELECT game FROM TablaGame game
                 WHERE (game.firstPlayer.username = :username
                    OR game.secondPlayer.username = :username)
                 AND game.winner IS NULL
                 ORDER BY game.createdAt DESC
            """)
    List<TablaGame> findActiveGamesByUsername(@Param("username") String username);

    /**
     * The id of the game this player is still in, newest first — the id alone,
     * for the question asked each time a game screen opens, so the seats and
     * the state are not loaded just to be thrown away.
     */
    @Query("""
                 SELECT game.id FROM TablaGame game
                 WHERE (game.firstPlayer.username = :username
                    OR game.secondPlayer.username = :username)
                 AND game.winner IS NULL
                 ORDER BY game.createdAt DESC
            """)
    List<UUID> findActiveGameIdsByUsername(@Param("username") String username);

    /** Every game still being played: what the turn timer re-arms after a restart. */
    @Query("SELECT game FROM TablaGame game WHERE game.winner IS NULL")
    List<TablaGame> findAllActive();
}
