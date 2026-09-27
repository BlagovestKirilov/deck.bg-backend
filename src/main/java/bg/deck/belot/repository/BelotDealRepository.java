package bg.deck.belot.repository;

import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BelotDealRepository extends JpaRepository<BelotDeal, UUID> {

    /** Deals whose turn started before this moment — candidates for the sweep. */
    List<BelotDeal> findByStatusInAndTurnStartedAtBefore(
            List<BelotDealStatus> statuses, Instant startedBefore);

    /** Every hand at this table, oldest first — the score sheet. */
    List<BelotDeal> findByGameOrderByDealNumberAsc(BelotGame game);

    /** The deal being played at this table, if one is. */
    Optional<BelotDeal> findFirstByGameOrderByDealNumberDesc(BelotGame game);
}
