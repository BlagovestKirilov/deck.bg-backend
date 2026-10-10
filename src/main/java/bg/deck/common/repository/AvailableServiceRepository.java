package bg.deck.common.repository;

import bg.deck.common.model.AvailableService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AvailableServiceRepository extends JpaRepository<AvailableService, UUID> {
}
