package bg.deck.belot.repository;

import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BelotDealRepository extends JpaRepository<BelotDeal, UUID> {

    /** Deals in any of these states — the ones with a turn on, for the turn timer. */
    List<BelotDeal> findByStatusIn(List<BelotDealStatus> statuses);

    /** Every hand at this table, oldest first — the score sheet. */
    List<BelotDeal> findByGameOrderByDealNumberAsc(BelotGame game);

    /** The deal being played at this table, if one is. */
    Optional<BelotDeal> findFirstByGameOrderByDealNumberDesc(BelotGame game);
}
