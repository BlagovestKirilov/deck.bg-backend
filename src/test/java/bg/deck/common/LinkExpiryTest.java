package bg.deck.common;

import bg.deck.common.enums.EmailConfirmationStatus;
import bg.deck.common.enums.ForgotPasswordStatus;
import bg.deck.common.enums.UserDeletionStatus;
import bg.deck.common.exception.InvalidLinkException;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.User;
import bg.deck.common.model.UserDeletion;
import bg.deck.common.model.base.BaseEntity;
import bg.deck.common.model.request.ChangeForgottenPasswordRequest;
import bg.deck.common.service.AuthService;
import bg.deck.common.service.EmailConfirmationService;
import bg.deck.common.service.ForgotPasswordService;
import bg.deck.common.service.UserAccountService;
import bg.deck.common.service.UserDeletionService;
import bg.deck.common.service.UserService;
import bg.deck.common.service.UserUtilService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static bg.deck.common.constant.Constants.EMAIL_CONFIRMATION_VALIDITY;
import static bg.deck.common.constant.Constants.LINK_VALIDITY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A link from an email stops working after it was sent: a reset or a deletion
 * within the quarter of an hour it was asked in, a new player's confirmation
 * within the day they signed up.
 */
@DisplayName("How long a link from an email lasts")
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class LinkExpiryTest {

    /** Old enough that the link is gone; young enough that it still works. */
    private static final Duration TOO_OLD = LINK_VALIDITY.plusMinutes(1);
    private static final Duration STILL_GOOD = LINK_VALIDITY.minusMinutes(1);
    private static final Duration CONFIRMATION_TOO_OLD = EMAIL_CONFIRMATION_VALIDITY.plusMinutes(1);
    private static final Duration CONFIRMATION_STILL_GOOD = EMAIL_CONFIRMATION_VALIDITY.minusMinutes(1);

    private static final UUID TOKEN = UUID.fromString("2316a5c0-3765-494b-bd2b-8021fbcfb55a");

    @Nested
    @DisplayName("The password reset")
    class PasswordReset {

        @Mock private ForgotPasswordService forgotPasswordService;
        @Mock private UserAccountService userAccountService;
        @InjectMocks private AuthService authService;

        @Test
        @DisplayName("opened in time, it verifies")
        void freshLinkVerifies() {
            ForgotPassword forgotPassword = aged(new ForgotPassword(user()), STILL_GOOD);
            when(forgotPasswordService.findPending(TOKEN))
                    .thenReturn(Optional.of(forgotPassword));

            assertDoesNotThrow(() -> authService.verifyForgotPasswordToken(TOKEN));
            assertEquals(ForgotPasswordStatus.PENDING, forgotPassword.getStatus());
        }

        @Test
        @DisplayName("opened too late, it is refused and retired")
        void staleLinkIsRefused() {
            ForgotPassword forgotPassword = aged(new ForgotPassword(user()), TOO_OLD);
            when(forgotPasswordService.findPending(TOKEN))
                    .thenReturn(Optional.of(forgotPassword));

            assertThrows(InvalidLinkException.class, () -> authService.verifyForgotPasswordToken(TOKEN));
            assertEquals(ForgotPasswordStatus.EXPIRED, forgotPassword.getStatus(), "and it is not asked about twice");
            verify(forgotPasswordService).save(forgotPassword);
        }

        @Test
        @DisplayName("a password cannot be set through a link that has run out")
        void staleLinkCannotSetAPassword() {
            ForgotPassword forgotPassword = aged(new ForgotPassword(user()), TOO_OLD);
            when(forgotPasswordService.findPending(TOKEN))
                    .thenReturn(Optional.of(forgotPassword));

            assertThrows(InvalidLinkException.class, () -> authService.changeForgottenPassword(
                    new ChangeForgottenPasswordRequest("a-brand-new-password", TOKEN)));
            verifyNoInteractions(userAccountService);
        }
    }

    @Nested
    @DisplayName("The address confirmation, which lasts a day")
    class AddressConfirmation {

        @Mock private EmailConfirmationService emailConfirmationService;
        @InjectMocks private AuthService authService;

        @Test
        @DisplayName("opened within the day, the address is confirmed")
        void freshLinkConfirms() {
            User user = unconfirmedUser();
            EmailConfirmation confirmation = aged(new EmailConfirmation(user), CONFIRMATION_STILL_GOOD);
            when(emailConfirmationService.findPending(TOKEN))
                    .thenReturn(Optional.of(confirmation));

            assertEquals(true, authService.confirmEmail(TOKEN));
            assertEquals(EmailConfirmationStatus.CONFIRMED, confirmation.getStatus());
            assertEquals(true, user.getIsEmailConfirmed());
        }

        @Test
        @DisplayName("opened a day late, the address stays unconfirmed")
        void staleLinkDoesNotConfirm() {
            User user = unconfirmedUser();
            EmailConfirmation confirmation = aged(new EmailConfirmation(user), CONFIRMATION_TOO_OLD);
            when(emailConfirmationService.findPending(TOKEN))
                    .thenReturn(Optional.of(confirmation));

            assertFalse(authService.confirmEmail(TOKEN));
            assertEquals(EmailConfirmationStatus.EXPIRED, confirmation.getStatus());
            assertFalse(Boolean.TRUE.equals(user.getIsEmailConfirmed()));
        }
    }

    @Nested
    @DisplayName("The account deletion")
    class AccountDeletion {

        @Mock private UserDeletionService userDeletionService;
        @Mock private UserUtilService userUtilService;
        @InjectMocks private UserService userService;

        @Test
        @DisplayName("opened too late, nothing is deleted")
        void staleLinkDeletesNothing() {
            UserDeletion deletion = aged(new UserDeletion(user()), TOO_OLD);
            when(userDeletionService.findPending(TOKEN))
                    .thenReturn(Optional.of(deletion));

            assertFalse(userService.confirmDeletion(TOKEN));
            assertEquals(UserDeletionStatus.EXPIRED, deletion.getStatus());
            verify(userUtilService, never()).deleteUser(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("opened in time, the account goes")
        void freshLinkDeletes() {
            User user = user();
            UserDeletion deletion = aged(new UserDeletion(user), STILL_GOOD);
            when(userDeletionService.findPending(TOKEN))
                    .thenReturn(Optional.of(deletion));

            assertEquals(true, userService.confirmDeletion(TOKEN));
            assertEquals(UserDeletionStatus.SUCCESS, deletion.getStatus());
            verify(userUtilService).deleteUser(user);
        }
    }

    /* ---------------- helpers ---------------- */

    private static User user() {
        User user = new User();
        user.setUsername("petko91");
        user.setEmail("petko@example.com");
        user.setIsEmailConfirmed(true);
        return user;
    }

    /** Someone who has signed up and not yet opened their confirmation link. */
    private static User unconfirmedUser() {
        User user = user();
        user.setIsEmailConfirmed(false);
        return user;
    }

    /** The same row, as though it had been written that long ago. */
    private static <T extends BaseEntity> T aged(T entity, Duration age) {
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now().minus(age));
        return entity;
    }
}
