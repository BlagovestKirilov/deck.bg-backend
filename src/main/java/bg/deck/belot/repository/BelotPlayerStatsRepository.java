package bg.deck.belot.repository;

import bg.deck.belot.model.BelotPlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BelotPlayerStatsRepository extends JpaRepository<BelotPlayerStats, UUID> {

    Optional<BelotPlayerStats> findByUsername(String username);
}
