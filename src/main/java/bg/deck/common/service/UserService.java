package bg.deck.common.service;

import bg.deck.common.util.AuthenticatedUser;
import bg.deck.common.constant.Constants;
import bg.deck.common.constant.ExceptionConstants;
import bg.deck.common.constant.LogConstants;
import bg.deck.common.enums.UserDeletionStatus;
import bg.deck.common.exception.EmailNotConfirmedException;
import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.exception.InvalidPasswordException;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.User;
import bg.deck.common.model.UserDeletion;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.model.request.ChangePasswordRequest;
import bg.deck.common.model.request.UserDeletionRequest;
import bg.deck.common.model.response.ProfileResponse;
import bg.deck.common.util.TokenFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserAccountService userAccountService;
    private final EmailConfirmationService emailConfirmationService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserUtilService userUtilService;
    private final UserDeletionService userDeletionService;
    /** Every game's record, in the order the profile lists them. */
    private final List<GameRecordProvider> recordProviders;

    public ProfileResponse getProfile() {
        String username = AuthenticatedUser.username();

        log.info(LogConstants.TRY_GET_PROFILE, username);

        User user = userAccountService.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException(username));

        // Each game gives its own record; the profile does not know which games
        // there are.
        Map<String, GameStatsDTO> stats = new LinkedHashMap<>();
        recordProviders.forEach(provider -> stats.put(provider.code(), provider.recordOf(username)));
        // The santase* fields and rank are the legacy shape, for the client that
        // is deployed right now.
        GameStatsDTO santase = stats.get(Constants.LEGACY_PROFILE_GAME);
        return ProfileResponse.builder()
                .emailConfirmed(Boolean.TRUE.equals(user.getIsEmailConfirmed()))
                .stats(stats)
                .santaseWins(santase.wins())
                .santaseLosses(santase.losses())
                .rank(santase.rank())
                .build();
    }

    public boolean confirmEmail() {
        String username = AuthenticatedUser.username();

        log.info(LogConstants.EMAIL_CONFIRM_ATTEMPT, username);

        User user = userAccountService.requireByUsername(username);

        if (Boolean.TRUE.equals(user.getIsEmailConfirmed())) {
            log.info(LogConstants.EMAIL_CONFIRMATION_ALREADY_CONFIRMED, username);
            return false;
        }

        // Asking again ends the link before it: only the newest one works.
        EmailConfirmation emailConfirmation = emailConfirmationService.issueFor(user);

        emailService.sendConfirmationEmail(emailConfirmation);

        log.info(LogConstants.EMAIL_SEND_SUCCESS, username);

        return true;
    }

    @Transactional
    public void changePassword(ChangePasswordRequest changePasswordRequest) {
        String username = AuthenticatedUser.username();

        log.info(LogConstants.PASSWORD_CHANGE_STARTED, username);

        User user = userAccountService.requireByUsername(username);

        if (Boolean.FALSE.equals(user.getIsEmailConfirmed())) {
            log.warn(LogConstants.EMAIL_NOT_CONFIRMED, username);
            throw new EmailNotConfirmedException(user.getEmail());
        }

        if (!passwordEncoder.matches(changePasswordRequest.currentPassword(), user.getPassword())) {
            log.warn(LogConstants.INVALID_CURRENT_PASSWORD, username);
            throw new InvalidCredentialsException(username);
        }

        if (passwordEncoder.matches(changePasswordRequest.newPassword(), user.getPassword())) {
            log.warn(LogConstants.SAME_PASSWORD, username);
            throw new InvalidPasswordException(ExceptionConstants.SAME_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(changePasswordRequest.newPassword()));
        userAccountService.save(user);

        log.info(LogConstants.PASSWORD_CHANGE_SUCCESS, username);
    }

    @Transactional
    public void sendUserDeletionEmail(UserDeletionRequest userDeletionRequest) {
        String username = AuthenticatedUser.username();

        log.info(LogConstants.USER_DELETION_EMAIL_REQUESTED, username);

        User user = userAccountService.requireByUsername(username);

        if (Boolean.FALSE.equals(user.getIsEmailConfirmed())) {
            log.warn(LogConstants.EMAIL_NOT_CONFIRMED, username);
            throw new EmailNotConfirmedException(user.getEmail());
        }

        if (!passwordEncoder.matches(userDeletionRequest.password(), user.getPassword())) {
            log.warn(LogConstants.INVALID_PASSWORD, username);
            throw new InvalidCredentialsException(username);
        }

        // Asking again ends the link before it: only the newest one works.
        UserDeletion userDeletion = userDeletionService.issueFor(user);
        log.info(LogConstants.USER_DELETION_RECORD_CREATED, username, userDeletion.getId());

        emailService.sendDeletionEmail(userDeletion);
        log.info(LogConstants.USER_DELETION_EMAIL_SENT, user.getEmail());
    }

    @Transactional
    public boolean confirmDeletion(UUID userDeletionToken) {
        log.info(LogConstants.USER_DELETION_CONFIRM_ATTEMPT, TokenFingerprint.of(userDeletionToken));

        Optional<UserDeletion> optionalUserDeletion = userDeletionService.findPending(userDeletionToken);

        if (optionalUserDeletion.isEmpty()) {
            log.warn(LogConstants.USER_DELETION_TOKEN_INVALID);
            return false;
        }

        UserDeletion userDeletion = optionalUserDeletion.get();

        if (userDeletion.isOlderThan(Constants.LINK_VALIDITY)) {
            userDeletion.setStatus(UserDeletionStatus.EXPIRED);
            userDeletionService.save(userDeletion);
            log.warn(LogConstants.LINK_EXPIRED, TokenFingerprint.of(userDeletionToken));
            return false;
        }

        String username = userDeletion.getUser().getUsername();

        log.info(LogConstants.USER_DELETION_CONFIRMED, username);
        log.info(LogConstants.USER_DELETION_STARTED, username);

        userUtilService.deleteUser(userDeletion.getUser());

        userDeletion.setStatus(UserDeletionStatus.SUCCESS);
        userDeletionService.save(userDeletion);

        log.info(LogConstants.USER_DELETION_SUCCESS, username);
        return true;
    }
}
