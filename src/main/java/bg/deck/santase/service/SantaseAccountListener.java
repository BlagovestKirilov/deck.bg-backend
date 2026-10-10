package bg.deck.santase.service;

import bg.deck.common.model.event.UserDeleted;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Santase letting go of a deleted account's record.
 *
 * <p>Told about the deletion rather than reaching into the accounts table, as
 * belot is. Runs after the deletion has committed, in a transaction of its
 * own — there is nothing left to join by then.
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class SantaseAccountListener {

    private final SantaseStatsService santaseStatsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDeleted(UserDeleted event) {
        boolean stats = santaseStatsService.forget(event.username());
        log.info("Santase: let go of {} — record {}", event.username(), stats ? "removed" : "not found");
    }
}
