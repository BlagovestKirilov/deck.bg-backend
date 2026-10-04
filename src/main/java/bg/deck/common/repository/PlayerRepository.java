package bg.deck.common.repository;

import bg.deck.common.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    /** A user now owns one row per game played, newest last. */
    List<Player> findAllByUserUsername(String username);
}