package bg.deck.santase.repository;

import bg.deck.santase.model.GameState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Santase deal state. Owned by SantaseDealService, and spoken to by nothing else. */
public interface SantaseGameStateRepository extends JpaRepository<GameState, UUID> {
}
