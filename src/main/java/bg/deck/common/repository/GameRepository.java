package bg.deck.common.repository;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {
    @Query("""
                 SELECT game FROM Game game
                 WHERE (game.firstPlayer.user.username = :username
                    OR game.secondPlayer.user.username = :username)
                 AND game.winner IS NULL
                 ORDER BY game.createdAt DESC
            """)
    List<Game> findActiveGamesByUsername(@Param("username") String username);

    /**
     * Active game of one specific type. Without the type filter a табла search
     * would be refused while a Santase game is still running.
     */
    @Query("""
                 SELECT game FROM Game game
                 WHERE (game.firstPlayer.user.username = :username
                    OR game.secondPlayer.user.username = :username)
                 AND game.gameType = :gameType
                 AND game.winner IS NULL
                 ORDER BY game.createdAt DESC
            """)
    List<Game> findActiveGamesByUsernameAndType(@Param("username") String username,
                                                @Param("gameType") GameType gameType);

    /** Every unfinished game across all types — powers the hub's resume badge. */
    @Query("SELECT game FROM Game game WHERE game.winner IS NULL")
    List<Game> findAllActive();
}
