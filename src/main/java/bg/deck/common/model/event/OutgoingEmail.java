package bg.deck.common.model.event;

import java.time.Instant;

/**
 * One account email, fully built, waiting to be sent.
 *
 * <p>Published by {@code EmailService} on the thread that asked for the email
 * and delivered by its after-commit listener on another one — so it carries
 * only plain strings, never an entity that would need a session to read.
 *
 * <p>{@code queuedAt} is the moment the email was asked for, and it becomes the
 * message's Date. Sending happens later and elsewhere, so without it the mail
 * client orders these by when they happened to reach the mail server — and the
 * newest link, the only one still valid, would not be the newest email.
 *
 * @param to       the recipient's address
 * @param subject  the subject line
 * @param html     the rendered body
 * @param queuedAt when the email was asked for
 */
public record OutgoingEmail(String to, String subject, String html, Instant queuedAt) {

    public OutgoingEmail(String to, String subject, String html) {
        this(to, subject, html, Instant.now());
    }
}
