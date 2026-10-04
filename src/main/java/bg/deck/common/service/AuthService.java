package bg.deck.common.service;

import bg.deck.common.constant.Constants;
import bg.deck.common.constant.ExceptionConstants;
import bg.deck.common.constant.LogConstants;
import bg.deck.common.enums.EmailConfirmationStatus;
import bg.deck.common.enums.ForgotPasswordStatus;
import bg.deck.common.exception.EmailAlreadyExistsException;
import bg.deck.common.exception.EmailNotConfirmedException;
import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.exception.InvalidLinkException;
import bg.deck.common.exception.InvalidPasswordException;
import bg.deck.common.exception.InvalidTokenException;
import bg.deck.common.exception.UserAlreadyExistsException;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.enums.GameType;
import bg.deck.common.model.User;
import bg.deck.common.model.UserGameStats;
import bg.deck.common.model.request.ChangeForgottenPasswordRequest;
import bg.deck.common.model.request.ForgotPasswordEmailRequest;
import bg.deck.common.model.request.LoginRequest;
import bg.deck.common.model.request.RegisterRequest;
import bg.deck.common.model.response.AuthResponse;
import bg.deck.common.util.TokenFingerprint;
import bg.deck.common.util.UserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static bg.deck.common.constant.Constants.CF_CONNECTING_IP;
import static bg.deck.common.constant.LogConstants.SUCCESSFUL_LOGIN_LOG;
import static bg.deck.common.constant.LogConstants.SUCCESSFUL_REGISTER_LOG;
import static bg.deck.common.constant.LogConstants.TRY_LOGIN_LOG;
import static bg.deck.common.constant.LogConstants.TRY_REGISTER_LOG;

@Log4j2
@Transactional
@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserAccountService userAccountService;
    private final ForgotPasswordService forgotPasswordService;
    private final EmailConfirmationService emailConfirmationService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final EmailService emailService;

    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        log.info(TRY_LOGIN_LOG, loginRequest.username());

        User user = userAccountService.findByUsername(loginRequest.username())
                .filter(foundUser -> passwordEncoder.matches(loginRequest.password(), foundUser.getPassword()))
                .orElseThrow(() -> new InvalidCredentialsException(loginRequest.username()));

        log.info(SUCCESSFUL_LOGIN_LOG, loginRequest.username());

        user.setIpAddress(request.getHeader(CF_CONNECTING_IP));
        userAccountService.save(user);

        return AuthResponse.builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message(LogConstants.SUCCESSFUL_LOGIN)
                .token(jwtService.generateToken(user))
                .refreshToken(jwtService.generateRefreshToken(user))
                .build();
    }

    public AuthResponse register(RegisterRequest registerRequest) {
        log.info(TRY_REGISTER_LOG, registerRequest.username());

        if (userAccountService.existsByUsername(registerRequest.username())) {
            throw new UserAlreadyExistsException(registerRequest.username());
        }

        if (userAccountService.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyExistsException(registerRequest.email());
        }

        User user = userMapper.toEntity(registerRequest);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userAccountService.save(user);

        // Both stats rows are created up front: lazy creation on first game would
        // need INSERT ... ON CONFLICT handling under concurrency, and one extra
        // row per user is cheaper than that.
        user.addStats(UserGameStats.fresh(user, GameType.SANTASE));
        user.addStats(UserGameStats.fresh(user, GameType.TABLA));
        userAccountService.save(user);

        EmailConfirmation emailConfirmation = emailConfirmationService.issueFor(user);

        emailService.sendConfirmationEmail(emailConfirmation);

        log.info(SUCCESSFUL_REGISTER_LOG, registerRequest.username());

        return AuthResponse.builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message(LogConstants.SUCCESSFUL_REGISTER)
                .build();
    }

    public AuthResponse refreshToken(String refreshToken) {
        String username;
        try {
            username = jwtService.extractUsername(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            // Expired, tampered with, or not a token at all: one answer for all
            // three, and none of them a fault of this server. It used to escape
            // as a 500 carrying the library's exception name.
            log.warn(LogConstants.REFRESH_TOKEN_UNREADABLE, ex.getClass().getSimpleName());
            throw new InvalidTokenException();
        }

        log.info(LogConstants.TRY_REFRESH_TOKEN, username);

        User user = userAccountService.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException(username));

        if (username != null && jwtService.isTokenValid(refreshToken)) {

            log.info(LogConstants.SUCCESSFUL_REFRESH_TOKEN);

            return AuthResponse.builder()
                    .status(HttpStatus.OK.getReasonPhrase())
                    .message(LogConstants.SUCCESSFUL_REFRESH_TOKEN)
                    .token(jwtService.generateToken(user))
                    .refreshToken(jwtService.generateRefreshToken(user))
                    .build();
        }

        throw new InvalidTokenException();
    }

    public boolean confirmEmail(UUID confirmationToken) {
        log.info(LogConstants.EMAIL_CONFIRMATION_ATTEMPT, TokenFingerprint.of(confirmationToken));

        Optional<EmailConfirmation> optionalEmailConfirmation =
                emailConfirmationService.findPending(confirmationToken);

        if (optionalEmailConfirmation.isEmpty()) {
            log.warn(LogConstants.EMAIL_CONFIRMATION_TOKEN_NOT_FOUND, TokenFingerprint.of(confirmationToken));
            return false;
        }

        EmailConfirmation emailConfirmation = optionalEmailConfirmation.get();

        if (emailConfirmation.isOlderThan(Constants.EMAIL_CONFIRMATION_VALIDITY)) {
            emailConfirmation.setStatus(EmailConfirmationStatus.EXPIRED);
            emailConfirmationService.save(emailConfirmation);
            log.warn(LogConstants.LINK_EXPIRED, TokenFingerprint.of(confirmationToken));
            return false;
        }

        if (emailConfirmation.getUser() == null) {
            log.warn(LogConstants.EMAIL_CONFIRMATION_TOKEN_NOT_FOUND, TokenFingerprint.of(confirmationToken));
            return false;
        }

        if (EmailConfirmationStatus.CONFIRMED.equals(emailConfirmation.getStatus())) {
            log.info(
                    LogConstants.EMAIL_ALREADY_CONFIRMED,
                    confirmationToken,
                    emailConfirmation.getUser().getUsername()
            );
            return false;
        }

        emailConfirmation.setStatus(EmailConfirmationStatus.CONFIRMED);
        emailConfirmation.getUser().setIsEmailConfirmed(true);

        emailConfirmationService.save(emailConfirmation);

        log.info(
                LogConstants.EMAIL_CONFIRMED_SUCCESSFULLY,
                confirmationToken,
                emailConfirmation.getUser().getUsername()
        );

        return true;
    }

    public void forgotPassword(ForgotPasswordEmailRequest forgotPasswordEmailRequest) {
        String email = forgotPasswordEmailRequest.email();

        log.info(LogConstants.FORGOT_PASSWORD_STARTED, email);

        Optional<User> account = userAccountService.findByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getIsEmailConfirmed()));

        // An address with no account, and one with an unconfirmed account, get
        // the same answer as one that exists: otherwise this endpoint tells
        // anyone who asks which addresses are registered here, which is a list
        // worth having before a password-guessing run.
        if (account.isEmpty()) {
            log.warn(LogConstants.FORGOT_PASSWORD_EMAIL_NOT_CONFIRMED, email);
            return;
        }

        // Asking again ends the link before it: only the newest one works.
        ForgotPassword forgotPassword = forgotPasswordService.issueFor(account.get());

        emailService.sendForgotPasswordEmail(forgotPassword);

        log.info(LogConstants.FORGOT_PASSWORD_EMAIL_SENT, email);
    }

    public void changeForgottenPassword(ChangeForgottenPasswordRequest changeForgottenPasswordRequest) {
        ForgotPassword forgotPassword = pendingForgotPassword(changeForgottenPasswordRequest.token());

        User user = forgotPassword.getUser();

        log.info(LogConstants.PASSWORD_CHANGE_STARTED, user.getUsername());

        if (Boolean.FALSE.equals(user.getIsEmailConfirmed())) {
            log.warn(LogConstants.EMAIL_NOT_CONFIRMED, user.getUsername());
            throw new EmailNotConfirmedException(user.getEmail());
        }

        if (passwordEncoder.matches(changeForgottenPasswordRequest.newPassword(), user.getPassword())) {
            log.warn(LogConstants.SAME_PASSWORD, user.getUsername());
            throw new InvalidPasswordException(ExceptionConstants.SAME_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(changeForgottenPasswordRequest.newPassword()));
        userAccountService.save(user);

        forgotPassword.setStatus(ForgotPasswordStatus.SUCCESS);
        forgotPasswordService.save(forgotPassword);

        log.info(LogConstants.PASSWORD_CHANGE_SUCCESS, user.getUsername());
    }

    public void verifyForgotPasswordToken(UUID token) {
        pendingForgotPassword(token);
    }

    /**
     * The reset still open under this token, or no reset at all.
     *
     * <p>A link that has outlived {@link Constants#LINK_VALIDITY} is retired on
     * the way past, so the row says what the answer already was and a second
     * attempt takes the short path.
     */
    private ForgotPassword pendingForgotPassword(UUID token) {
        ForgotPassword forgotPassword = forgotPasswordService.findPending(token)
                .orElseThrow(InvalidLinkException::new);

        if (forgotPassword.isOlderThan(Constants.LINK_VALIDITY)) {
            forgotPassword.setStatus(ForgotPasswordStatus.EXPIRED);
            forgotPasswordService.save(forgotPassword);
            log.warn(LogConstants.LINK_EXPIRED, TokenFingerprint.of(token));
            throw new InvalidLinkException();
        }

        return forgotPassword;
    }
}
