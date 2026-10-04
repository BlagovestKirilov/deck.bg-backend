package bg.deck.common.service;

import bg.deck.common.config.TemplateLoader;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.User;
import bg.deck.common.model.UserDeletion;
import bg.deck.common.model.event.OutgoingEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Account emails are sent off the request thread, and only once the
 * transaction that created their token has committed.
 *
 * <p>A real Spring context, so the event, async and after-commit machinery is
 * the production one; only the mail server and the database are stand-ins.
 */
@DisplayName("Sending account emails")
@SpringJUnitConfig(EmailTestConfig.class)
class EmailServiceTest {

    @Autowired private EmailService emailService;
    @Autowired private JavaMailSender mailSender;
    @Autowired private PlatformTransactionManager transactionManager;

    /** The thread each send ran on. */
    private final AtomicReference<Thread> sentOn = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> {
            sentOn.set(Thread.currentThread());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("outside a transaction, it is sent at once — on another thread")
    void sentOffTheRequestThread() throws Exception {
        EmailConfirmation confirmation = new EmailConfirmation(user());

        emailService.sendConfirmationEmail(confirmation);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000)).send(sent.capture());
        assertNotEquals(Thread.currentThread(), sentOn.get(), "the request did not wait for the mail server");

        // And it is the whole email, built before it left the request.
        MimeMessage message = sent.getValue();
        assertEquals("petko@example.com", message.getAllRecipients()[0].toString());
        assertEquals("DECK BG", ((InternetAddress) message.getFrom()[0]).getPersonal());
        assertEquals("Потвърди своя профил в DECK.bg", message.getSubject());
        String html = htmlOf(message);
        assertTrue(html.contains("petko91"), "username filled in");
        assertTrue(html.contains(confirmation.getConfirmationToken().toString()), "link carries the token");
        assertTrue(html.contains("Линкът важи 24 часа."), "and says how long it lasts");
        assertTrue(!html.contains("{{"), "no placeholder left");
    }

    @Test
    @DisplayName("inside a transaction, it waits for the commit")
    void waitsForTheCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailService.sendForgotPasswordEmail(new ForgotPassword(user()));
            pause(400);
            verify(mailSender, never()).send(any(MimeMessage.class));
        });

        verify(mailSender, timeout(3000)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a token that was rolled back is never mailed")
    void notSentOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailService.sendForgotPasswordEmail(new ForgotPassword(user()));
            status.setRollbackOnly();
        });

        pause(600);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a deletion email gives the same fifteen minutes")
    void deletionEmailStatesItsValidity() throws Exception {
        emailService.sendDeletionEmail(new UserDeletion(user()));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000)).send(sent.capture());

        String html = htmlOf(sent.getValue());
        assertTrue(html.contains("Линкът важи 15 минути."), "the email promises what the server keeps");
        assertTrue(!html.contains("{{"), "no placeholder left");
    }

    @Test
    @DisplayName("a reset email gives the fifteen minutes the server allows")
    void resetEmailStatesItsValidity() throws Exception {
        emailService.sendForgotPasswordEmail(new ForgotPassword(user()));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(3000)).send(sent.capture());

        String html = htmlOf(sent.getValue());
        assertTrue(html.contains("Линкът важи 15 минути."), "the email promises what the server keeps");
        assertTrue(!html.contains("{{"), "no placeholder left");
    }

    @Test
    @DisplayName("links asked for in a row arrive in that order, and carry the time they were asked for")
    void keepsTheOrderTheyWereAskedIn() throws Exception {
        // Asking again expires the link before it, so the last email must be
        // the last link — otherwise the one on top of the inbox is dead.
        List<ForgotPassword> asked = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ForgotPassword forgotPassword = new ForgotPassword(user());
            asked.add(forgotPassword);
            emailService.sendForgotPasswordEmail(forgotPassword);
        }

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(5)).send(sent.capture());

        List<MimeMessage> messages = sent.getAllValues();
        for (int i = 0; i < 5; i++) {
            String token = asked.get(i).getForgotPasswordToken().toString();
            assertTrue(htmlOf(messages.get(i)).contains(token), "email " + (i + 1) + " carries link " + (i + 1));
            if (i > 0) {
                assertFalse(
                        messages.get(i).getSentDate().before(messages.get(i - 1).getSentDate()),
                        "the date never goes backwards"
                );
            }
        }
    }

    @Test
    @DisplayName("a mail server that is down is logged, not thrown — there is no request left to fail")
    void failureIsLogged() {
        JavaMailSender down = mock(JavaMailSender.class);
        when(down.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("connection refused")).when(down).send(any(MimeMessage.class));
        EmailService service = new EmailService(down, new TemplateLoader(), mock(ApplicationEventPublisher.class));

        assertDoesNotThrow(() -> service.deliver(new OutgoingEmail("petko@example.com", "s", "<p>x</p>")));
        verify(down).send(any(MimeMessage.class));
    }

    /* ---------------- helpers ---------------- */

    private static User user() {
        User user = new User();
        user.setUsername("petko91");
        user.setEmail("petko@example.com");
        return user;
    }

    /** The decoded HTML, found by walking the multipart the helper builds. */
    private static String htmlOf(MimeMessage message) throws Exception {
        message.saveChanges();
        return htmlIn(message);
    }

    private static String htmlIn(jakarta.mail.Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.getContent() instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = htmlIn(multipart.getBodyPart(i));
                if (html != null) return html;
            }
        }
        return null;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
