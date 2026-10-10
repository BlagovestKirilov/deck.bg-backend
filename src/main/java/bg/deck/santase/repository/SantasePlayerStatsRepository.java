package bg.deck.santase.repository;

import bg.deck.santase.model.SantasePlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Santase records. Owned by SantaseStatsService, and spoken to by nothing else. */
public interface SantasePlayerStatsRepository extends JpaRepository<SantasePlayerStats, UUID> {

    Optional<SantasePlayerStats> findByUsername(String username);
}
