package bg.deck.common.repository;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {
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

    /** The game of this type the player is still in, if any. */
    @Query("""
                SELECT game.id FROM Game game
                WHERE (game.firstPlayer.user.username = :username
                   OR game.secondPlayer.user.username = :username)
                AND game.gameType = :gameType
                AND game.winner IS NULL
            """)
    Optional<UUID> findActiveGameIdByUsernameAndType(@Param("username") String username,
                                                     @Param("gameType") GameType gameType);

    /** Every unfinished game of one type: what that game's turn timer re-arms after a restart. */
    @Query("SELECT game FROM Game game WHERE game.winner IS NULL AND game.gameType = :gameType")
    List<Game> findAllActiveByType(@Param("gameType") GameType gameType);
}
