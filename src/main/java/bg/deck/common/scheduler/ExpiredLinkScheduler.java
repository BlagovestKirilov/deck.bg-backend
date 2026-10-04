package bg.deck.common.scheduler;

import bg.deck.common.constant.Constants;
import bg.deck.common.constant.LogConstants;
import bg.deck.common.service.EmailConfirmationService;
import bg.deck.common.service.ForgotPasswordService;
import bg.deck.common.service.UserDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Retires the links nobody came back for.
 *
 * <p>A link is refused on age the moment someone uses it, so this changes no
 * answer the service gives. What it changes is what the tables say: without it
 * a link nobody ever opens sits at PENDING for good, and reading the row tells
 * you the opposite of the truth — which is exactly how an expired reset once
 * looked like a working one.
 *
 * <p>Runs on its own schedule under a lock held in the database. During a
 * deploy the old instance and the new one overlap, and only one of them should
 * do this.
 *
 * @see bg.deck.common.config.SchedulingConfig for the pool, the lock and the timings
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class ExpiredLinkScheduler {

    /** The name this job holds its lock under. One row in {@code shedlock}. */
    public static final String LOCK_NAME = "expiredLinks";

    private final ForgotPasswordService forgotPasswordService;
    private final EmailConfirmationService emailConfirmationService;
    private final UserDeletionService userDeletionService;

    /**
     * {@code lockAtLeastFor} outlives the run itself: the work takes
     * milliseconds, and without a floor two instances whose clocks differ
     * slightly could each take a turn in the same minute. {@code lockAtMostFor}
     * is the other end — if this instance dies mid-run, the lock is not held
     * for ever.
     */
    @Transactional
    @Scheduled(
            fixedDelayString = "${deck.scheduling.expired-links.interval}",
            initialDelayString = "${deck.scheduling.expired-links.initial-delay}"
    )
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT30S", lockAtMostFor = "PT4M")
    public void expireLinks() {
        Instant now = Instant.now();
        long startedAt = System.nanoTime();

        int resets = forgotPasswordService.expireOlderThan(now.minus(Constants.LINK_VALIDITY));
        int confirmations = emailConfirmationService.expireOlderThan(now.minus(Constants.EMAIL_CONFIRMATION_VALIDITY));
        int deletions = userDeletionService.expireOlderThan(now.minus(Constants.LINK_VALIDITY));

        int total = resets + confirmations + deletions;
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;

        // A run that retired something is worth a line; the other 280-odd runs a
        // day are not, so they go to debug — the count is still there when the
        // level is turned up to look.
        if (total > 0) {
            log.info(LogConstants.LINKS_EXPIRED, total, resets, confirmations, deletions, tookMs);
        } else {
            log.debug(LogConstants.LINKS_EXPIRED_NONE, tookMs);
        }
    }
}
