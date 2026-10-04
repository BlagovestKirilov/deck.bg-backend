package bg.deck.common.repository;

import bg.deck.santase.model.GameState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameStateRepository extends JpaRepository<GameState, UUID> {
}
