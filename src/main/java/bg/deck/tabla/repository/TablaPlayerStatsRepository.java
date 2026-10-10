package bg.deck.tabla.repository;

import bg.deck.tabla.model.TablaPlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Табла records. Owned by TablaStatsService, and spoken to by nothing else. */
public interface TablaPlayerStatsRepository extends JpaRepository<TablaPlayerStats, UUID> {

    Optional<TablaPlayerStats> findByUsername(String username);
}
