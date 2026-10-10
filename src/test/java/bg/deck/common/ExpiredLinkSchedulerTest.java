package bg.deck.common;

import bg.deck.common.scheduler.ExpiredLinkScheduler;
import bg.deck.common.service.EmailConfirmationService;
import bg.deck.common.service.ForgotPasswordService;
import bg.deck.common.service.UserDeletionService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static bg.deck.common.constant.Constants.EMAIL_CONFIRMATION_VALIDITY;
import static bg.deck.common.constant.Constants.LINK_VALIDITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The job that keeps the tables saying what is true: a link nobody opened is
 * marked expired once its time is up, instead of sitting at PENDING for good.
 *
 * <p>It asks each table's own service, and never a repository.
 */
@DisplayName("Retiring the links nobody came back for")
@ExtendWith(MockitoExtension.class)
class ExpiredLinkSchedulerTest {

    @Mock private ForgotPasswordService forgotPasswordService;
    @Mock private EmailConfirmationService emailConfirmationService;
    @Mock private UserDeletionService userDeletionService;
    @InjectMocks private ExpiredLinkScheduler scheduler;

    @Test
    @DisplayName("each table is retired to its own cutoff")
    void retiresEachTableOnItsOwnCutoff() {
        Instant before = Instant.now();

        scheduler.expireLinks();

        Instant after = Instant.now();

        ArgumentCaptor<Instant> resets = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> confirmations = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> deletions = ArgumentCaptor.forClass(Instant.class);
        verify(forgotPasswordService).expireOlderThan(resets.capture());
        verify(emailConfirmationService).expireOlderThan(confirmations.capture());
        verify(userDeletionService).expireOlderThan(deletions.capture());

        // A reset and a deletion go after a quarter of an hour.
        assertCutoff(resets.getValue(), LINK_VALIDITY, before, after);
        assertCutoff(deletions.getValue(), LINK_VALIDITY, before, after);
        // A new player's confirmation has the whole day.
        assertCutoff(confirmations.getValue(), EMAIL_CONFIRMATION_VALIDITY, before, after);
    }

    /**
     * What the line in the log actually says. Five numbers in one message is
     * where an argument slips a place and nobody notices, because the sentence
     * still reads perfectly well.
     */
    @Test
    @DisplayName("the line says how many of each were retired")
    void logsWhatItRetired() {
        when(forgotPasswordService.expireOlderThan(any())).thenReturn(3);
        when(emailConfirmationService.expireOlderThan(any())).thenReturn(7);
        when(userDeletionService.expireOlderThan(any())).thenReturn(1);

        Capture capture = captureLogs(Level.INFO);
        scheduler.expireLinks();

        assertEquals(1, capture.lines.size(), "one line for the run");
        String line = capture.lines.getFirst();
        assertTrue(line.startsWith("Retired 11 expired links: 3 password resets, "
                + "7 email confirmations, 1 account deletions."), "but it said: " + line);
    }

    @Test
    @DisplayName("a run that retired nothing keeps out of the log")
    void quietWhenThereIsNothingToRetire() {
        Capture capture = captureLogs(Level.INFO);

        scheduler.expireLinks();

        assertEquals(List.of(), capture.lines, "288 runs a day, and nothing to say about most of them");
    }

    /* ---------------- helpers ---------------- */

    /** The cutoff is exactly one lifetime back from the moment of the run. */
    private static void assertCutoff(Instant cutoff, Duration validity, Instant before, Instant after) {
        assertTrue(!cutoff.isBefore(before.minus(validity)) && !cutoff.isAfter(after.minus(validity)),
                "taken back by " + validity + ", but the cutoff was " + cutoff);
    }

    /** Log4j2 without a whole configuration: one appender that keeps the lines. */
    private static final class Capture extends AbstractAppender {
        private final List<String> lines = new CopyOnWriteArrayList<>();

        private Capture() {
            super("capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lines.add(event.getMessage().getFormattedMessage());
        }
    }

    private Logger scheduledLogger;
    private Capture appender;

    private Capture captureLogs(Level level) {
        scheduledLogger = (Logger) org.apache.logging.log4j.LogManager.getLogger(ExpiredLinkScheduler.class);
        appender = new Capture();
        appender.start();
        scheduledLogger.addAppender(appender);
        scheduledLogger.setLevel(level);
        return appender;
    }

    @AfterEach
    void detachAppender() {
        if (scheduledLogger != null) {
            scheduledLogger.removeAppender(appender);
            scheduledLogger = null;
        }
    }
}
