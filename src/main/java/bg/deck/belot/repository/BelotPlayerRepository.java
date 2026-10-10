package bg.deck.belot.repository;

import bg.deck.belot.model.BelotPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BelotPlayerRepository extends JpaRepository<BelotPlayer, UUID> {
    Optional<BelotPlayer> findByUsername(String username);
}
