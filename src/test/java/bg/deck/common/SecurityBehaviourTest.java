package bg.deck.common;

import bg.deck.common.exception.InvalidTokenException;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.User;
import bg.deck.common.model.request.ForgotPasswordEmailRequest;
import bg.deck.common.service.AuthService;
import bg.deck.common.service.EmailService;
import bg.deck.common.service.ForgotPasswordService;
import bg.deck.common.service.JwtService;
import bg.deck.common.service.UserAccountService;
import bg.deck.common.util.TokenFingerprint;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The parts of the service that are there to hold under someone trying rather
 * than someone using it. Each of these was a finding in an OWASP Top 10:2025
 * review, and each is the kind of thing a later change can undo without any
 * visible symptom — which is what these tests are for.
 */
@DisplayName("What the service gives away, and to whom")
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class SecurityBehaviourTest {

    @Nested
    @DisplayName("Asking for a password reset")
    class PasswordReset {

        @Mock private UserAccountService userAccountService;
        @Mock private ForgotPasswordService forgotPasswordService;
        @Mock private EmailService emailService;
        @InjectMocks private AuthService authService;

        private static final ForgotPasswordEmailRequest ASK =
                new ForgotPasswordEmailRequest("stranger@example.com");

        @Test
        @DisplayName("an address with no account is answered exactly like one that has")
        void doesNotRevealThatTheAddressIsUnknown() {
            when(userAccountService.findByEmail(any())).thenReturn(Optional.empty());

            // No exception: an error here is the difference a stranger measures
            // to learn which addresses are registered.
            assertDoesNotThrow(() -> authService.forgotPassword(ASK));
            verifyNoInteractions(emailService);
            verify(forgotPasswordService, never()).issueFor(any());
        }

        @Test
        @DisplayName("an unconfirmed account is answered the same way, and gets no link")
        void doesNotRevealThatTheAccountIsUnconfirmed() {
            User user = new User();
            user.setEmail("stranger@example.com");
            user.setIsEmailConfirmed(false);
            when(userAccountService.findByEmail(any())).thenReturn(Optional.of(user));

            assertDoesNotThrow(() -> authService.forgotPassword(ASK));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("a confirmed account does get its link")
        void aRealAccountStillGetsTheEmail() {
            User user = new User();
            user.setUsername("petko91");
            user.setEmail("stranger@example.com");
            user.setIsEmailConfirmed(true);
            ForgotPassword issued = new ForgotPassword(user);
            when(userAccountService.findByEmail(any())).thenReturn(Optional.of(user));
            when(forgotPasswordService.issueFor(user)).thenReturn(issued);

            authService.forgotPassword(ASK);

            verify(emailService).sendForgotPasswordEmail(issued);
        }
    }

    @Nested
    @DisplayName("Renewing a session")
    class Refresh {

        @Mock private JwtService jwtService;
        @Mock private UserAccountService userAccountService;
        @InjectMocks private AuthService authService;

        @Test
        @DisplayName("an expired refresh token is refused, not a server error")
        void anExpiredRefreshTokenIsARefusal() {
            when(jwtService.extractUsername(any())).thenThrow(new ExpiredJwtException(null, null, "expired"));

            // Before: the exception escaped as a 500 carrying the library's
            // class name, which the client had to read to tell a dead session
            // from a server that was merely down.
            assertThrows(InvalidTokenException.class, () -> authService.refreshToken("expired.jwt"));
            verifyNoInteractions(userAccountService);
        }

        @Test
        @DisplayName("so is something that is not a token at all")
        void nonsenseIsARefusalToo() {
            when(jwtService.extractUsername(any())).thenThrow(new IllegalArgumentException("not a jwt"));

            assertThrows(InvalidTokenException.class, () -> authService.refreshToken("nonsense"));
        }
    }

    @Nested
    @DisplayName("Naming a link in the log")
    class Fingerprints {

        private static final UUID TOKEN = UUID.fromString("db7cd159-61da-4588-9ff6-7afd19a3b77c");

        @Test
        @DisplayName("the token itself never appears")
        void theTokenIsNotInTheFingerprint() {
            String fingerprint = TokenFingerprint.of(TOKEN);

            assertAll(
                    () -> assertFalse(fingerprint.contains(TOKEN.toString()), "the whole token"),
                    () -> assertFalse(fingerprint.contains("db7cd159"), "nor its first block"),
                    () -> assertEquals(12, fingerprint.length(), "twelve hex characters"),
                    () -> assertTrue(fingerprint.matches("[0-9a-f]{12}"), "and nothing but hex")
            );
        }

        @Test
        @DisplayName("the same link is the same name, a different link is not")
        void sameLinkSameName() {
            assertEquals(TokenFingerprint.of(TOKEN), TokenFingerprint.of(TOKEN),
                    "so one link can be followed across several lines");
            assertNotEquals(TokenFingerprint.of(TOKEN), TokenFingerprint.of(UUID.randomUUID()));
        }

        @Test
        @DisplayName("a missing token says so rather than throwing")
        void nullIsNamed() {
            assertEquals("none", TokenFingerprint.of(null));
        }
    }
}
