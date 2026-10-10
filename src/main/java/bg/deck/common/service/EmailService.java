package bg.deck.common.service;

import bg.deck.common.config.TemplateLoader;
import bg.deck.common.constant.LogConstants;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.UserDeletion;
import bg.deck.common.model.event.OutgoingEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static bg.deck.common.constant.Constants.DECK_BG_CONFIRM_EMAIL;
import static bg.deck.common.constant.Constants.DECK_BG_DELETE_ACCOUNT;
import static bg.deck.common.constant.Constants.DECK_BG_EMAIL;
import static bg.deck.common.constant.Constants.DECK_BG_EMAIL_SUBJECT;
import static bg.deck.common.constant.Constants.DECK_BG_FORGOT_PASSWORD;
import static bg.deck.common.constant.Constants.DECK_BG_PERSONAL;
import static bg.deck.common.constant.Constants.DELETION_SUBJECT;
import static bg.deck.common.constant.Constants.DELETION_TEMPLATE;
import static bg.deck.common.constant.Constants.EMAIL_CONFIRMATION_LINK;
import static bg.deck.common.constant.Constants.EMAIL_CONFIRMATION_TEMPLATE;
import static bg.deck.common.constant.Constants.EMAIL_USERNAME;
import static bg.deck.common.constant.Constants.EMAIL_CONFIRMATION_VALIDITY;
import static bg.deck.common.constant.Constants.EMAIL_VALIDITY;
import static bg.deck.common.constant.Constants.FORGOT_PASSWORD_SUBJECT;
import static bg.deck.common.constant.Constants.FORGOT_PASSWORD_TEMPLATE;
import static bg.deck.common.constant.Constants.LINK_VALIDITY;
import static bg.deck.common.constant.Constants.MAIL_EXECUTOR;

/**
 * Sends the account emails — confirmation, new password, deletion — without
 * making the request wait for the mail server.
 *
 * <p>Each email is built completely on the thread that asked for it: the
 * address comes from a lazily loaded user, which cannot be read once the
 * request has finished. Only plain strings cross to the sending thread.
 *
 * <p>It is sent after the caller's transaction commits, so a link is never
 * mailed for a token that was then rolled back; a caller with no transaction
 * has nothing to wait for, and the email goes at once. A failure is logged —
 * there is no request left to fail — and the person can ask again.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final TemplateLoader templateLoader;
    private final ApplicationEventPublisher events;

    public void sendConfirmationEmail(EmailConfirmation emailConfirmation) {
        queue(emailConfirmation.getUser().getEmail(), DECK_BG_EMAIL_SUBJECT, buildConfirmationBody(emailConfirmation));
    }

    public void sendForgotPasswordEmail(ForgotPassword forgotPassword) {
        queue(forgotPassword.getUser().getEmail(), FORGOT_PASSWORD_SUBJECT, buildForgotPasswordBody(forgotPassword));
    }

    public void sendDeletionEmail(UserDeletion userDeletion) {
        queue(userDeletion.getUser().getEmail(), DELETION_SUBJECT, buildDeletionBody(userDeletion));
    }

    private void queue(String to, String subject, String html) {
        events.publishEvent(new OutgoingEmail(to, subject, html));
    }

    /**
     * Runs off the request thread once the caller's transaction has committed,
     * or straight away when there was none — without {@code fallbackExecution},
     * an email queued outside a transaction would never be sent at all.
     *
     * <p>On {@code mailExecutor}, which is a single thread: several links asked
     * for in a row must arrive in the order they were made, because only the
     * last one still works.
     */
    @Async(MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(OutgoingEmail email) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.toString());

            helper.setFrom(sender());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            // When it was asked for, not when the mail server got round to it.
            helper.setSentDate(Date.from(email.queuedAt()));

            javaMailSender.send(message);
            log.info(LogConstants.EMAIL_SENT_LOG, email.to());
        } catch (MessagingException | MailException ex) {
            log.error(LogConstants.EMAIL_SEND_FAILED, email.to(), ex);
        }
    }

    /** The sender: DECK BG, from the no-reply address. See DECK_BG_PERSONAL. */
    static InternetAddress sender() throws AddressException {
        // Only letters and a space, so the name needs no quoting at all.
        return new InternetAddress(DECK_BG_PERSONAL + " <" + DECK_BG_EMAIL + ">");
    }

    private String buildConfirmationBody(EmailConfirmation emailConfirmation) {
        String link = DECK_BG_CONFIRM_EMAIL + emailConfirmation.getConfirmationToken();
        return buildEmailBody(EMAIL_CONFIRMATION_TEMPLATE, emailConfirmation.getUser().getUsername(), link,
                EMAIL_CONFIRMATION_VALIDITY);
    }

    private String buildForgotPasswordBody(ForgotPassword forgotPassword) {
        String link = DECK_BG_FORGOT_PASSWORD + forgotPassword.getForgotPasswordToken();
        return buildEmailBody(FORGOT_PASSWORD_TEMPLATE, forgotPassword.getUser().getUsername(), link, LINK_VALIDITY);
    }

    private String buildDeletionBody(UserDeletion userDeletion) {
        String link = DECK_BG_DELETE_ACCOUNT + userDeletion.getUserDeletionToken();
        return buildEmailBody(DELETION_TEMPLATE, userDeletion.getUser().getUsername(), link, LINK_VALIDITY);
    }

    private String buildEmailBody(String templatePath, String username, String link, Duration validity) {
        String template = templateLoader.load(templatePath);
        return template
                .replace(EMAIL_USERNAME, username)
                .replace(EMAIL_VALIDITY, inWords(validity))
                .replace(EMAIL_CONFIRMATION_LINK, link);
    }

    /**
     * How long the link lasts, in Bulgarian, taken from the same constant the
     * server checks against — so the email cannot promise one thing while the
     * server does another.
     */
    static String inWords(Duration validity) {
        if (validity.toMinutes() < 60) {
            long minutes = validity.toMinutes();
            return minutes + (minutes == 1 ? " минута" : " минути");
        }
        long hours = validity.toHours();
        return hours + (hours == 1 ? " час" : " часа");
    }
}
