package bg.deck.belot.service;

import bg.deck.belot.model.BelotMatchmaking;
import bg.deck.belot.repository.BelotMatchmakingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The matchmaking lock: the only place {@link BelotMatchmakingRepository} is
 * spoken to.
 *
 * <p>Its own service rather than a couple of lines inside
 * {@link BelotTableService}, because the two things it does need two
 * different transactions and a bean cannot give itself one — Spring applies
 * {@code @Transactional} with a proxy, and a method calling its own is not
 * going through it.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotMatchmakingService {

    private final BelotMatchmakingRepository belotMatchmakingRepository;

    /**
     * Blocks until whoever else is being seated has been.
     *
     * <p>Joins the caller's transaction on purpose, so the lock is released
     * when that transaction commits and the next player in reads a table with
     * the last one already sitting at it. {@code MANDATORY} rather than
     * {@code REQUIRED}: a lock taken in a transaction of its own would be
     * released immediately and would protect nothing, so being called outside
     * one is a mistake worth failing on.
     *
     * @return false when the row is not there yet, which only happens on a
     *         database the changeset has not reached
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lock() {
        return belotMatchmakingRepository.lock().isPresent();
    }

    /**
     * Creates the row, once, on a database that has never had it.
     *
     * <p>In its own transaction, and that is the whole reason this method
     * exists. Several players can reach a virgin database together and all
     * try to insert row 1; all but one fail on the primary key. Postgres
     * abandons a transaction that hits a constraint violation, so catching it
     * inside the caller's transaction would leave them holding a dead one —
     * which is exactly what happened, and it swallowed four of eight players'
     * joins before it was noticed.
     *
     * <p>The loser's violation is thrown, not swallowed here. Swallowing it
     * inside this method does not help: the transaction is already marked
     * rollback-only by then, and committing it throws anyway. The caller
     * catches it instead, by which point this transaction has rolled back on
     * its own and the caller's — suspended throughout — is untouched.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when
     *         another thread created the row first
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureRowExists() {
        belotMatchmakingRepository.save(new BelotMatchmaking(BelotMatchmaking.ROW));
        log.info("Belot: the matchmaking lock row was missing and has been created");
    }
}
