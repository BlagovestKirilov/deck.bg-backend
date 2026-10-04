package bg.deck.belot.service;

import bg.deck.common.model.event.UserDeleted;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Belot letting go of a deleted account.
 *
 * <p>The deletion happens in the public schema and belot is told about it,
 * rather than being reached into: the one thing that crosses the seam is a
 * username in an event, which is the same thing that crosses it on every
 * request.
 *
 * <p>Runs after the deletion has committed. Anonymising rows for an account
 * whose own deletion then rolled back would leave belot holding a table
 * nobody can be identified at.
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class BelotAccountListener {

    private final BelotPlayerService belotPlayerService;
    private final BelotTableService belotTableService;
    private final BelotStatsService belotStatsService;

    // REQUIRES_NEW, because the deletion’s own transaction has committed by
    // the time this runs: there is nothing left to join, and Spring refuses
    // a plain @Transactional here for exactly that reason.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDeleted(UserDeleted event) {
        int seats = belotTableService.anonymise(event.username());
        boolean player = belotPlayerService.forget(event.username());
        boolean stats = belotStatsService.forget(event.username());

        log.info("Belot: let go of {} — {} seat(s) renamed, player row {}, record {}",
                event.username(), seats, player ? "removed" : "not found",
                stats ? "removed" : "not found");
    }
}
