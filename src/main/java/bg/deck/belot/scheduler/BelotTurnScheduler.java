package bg.deck.belot.scheduler;

import bg.deck.belot.service.BelotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the tables moving when somebody stops answering.
 *
 * <p>Belot's own, not {@code GameInactivityService}: that one branches on
 * {@code GameType} and works on {@code Game}, which has two seats. This runs
 * on the same proven pattern — a schedule and a lock held in the database, so
 * the two instances that overlap during a deploy do not both act for the same
 * absent player.
 *
 * @see bg.deck.belot.config.BelotProperties for the timings
 * @see bg.deck.belot.service.BelotTurnService for what it does to a turn that ran out
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class BelotTurnScheduler {

    /** The name this job holds its lock under. One row in {@code shedlock}. */
    public static final String LOCK_NAME = "belotTurns";

    private final BelotService belotService;

    /**
     * {@code lockAtMostFor} is the safety net if this instance dies mid-sweep;
     * there is no {@code lockAtLeastFor}, because a sweep that runs twice in a
     * second finds nothing the first pass left behind — acting on a turn moves
     * the clock, and the second pass reads the moved one.
     */
    @Scheduled(
            fixedDelayString = "${deck.belot.turn-sweep}",
            initialDelayString = "${deck.belot.sweep-delay}"
    )
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT1M")
    public void actForAbsentPlayers() {
        long startedAt = System.nanoTime();
        int acted = belotService.actForAbsentPlayers();

        if (acted > 0) {
            log.info("Belot: acted for {} seat(s) that ran out of time. Took {} ms",
                    acted, (System.nanoTime() - startedAt) / 1_000_000);
        }
    }
}
