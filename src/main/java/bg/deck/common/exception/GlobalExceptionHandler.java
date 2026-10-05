package bg.deck.common.exception;

import bg.deck.common.constant.ExceptionConstants;
import bg.deck.common.constant.LogConstants;
import bg.deck.common.model.response.ErrorResponse;
import bg.deck.common.util.AuthenticatedUser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;
import bg.deck.santase.exception.CardNotFoundException;
import bg.deck.santase.exception.CardNotPlayableException;
import bg.deck.santase.exception.DeckSizeException;
import bg.deck.santase.exception.NoCardForReplacingException;
import bg.deck.santase.exception.NotFirstInTurnException;
import bg.deck.santase.exception.NotInTurnException;
import bg.deck.tabla.exception.TablaException;

import static bg.deck.common.constant.Constants.CF_CONNECTING_IP;
import static bg.deck.common.constant.ExceptionConstants.COMMA_DELIMITER;
import static bg.deck.common.constant.ExceptionConstants.INCORRECT_CREDENTIALS_MESSAGE;
import static bg.deck.common.constant.ExceptionConstants.LOG_FORMAT_SECURITY;
import static bg.deck.common.constant.ExceptionConstants.VALIDATION_DETAILS_FORMAT;

@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            CardNotFoundException.class,
            NoCardForReplacingException.class,
            NotFirstInTurnException.class,
            NotInTurnException.class,
            UserNotPartOfGameException.class,
            CardNotPlayableException.class,
            DeckSizeException.class,
            NoActiveGameFoundException.class,
            EmailConfirmationNotFoundException.class,
            EmailNotConfirmedException.class,
            UserNotFoundException.class,
            InvalidPasswordException.class,
            PlayerInactivitySurrenderException.class,
            TablaException.class
    })
    public ResponseEntity<ErrorResponse> handleSecurityAndBusinessExceptions(RuntimeException ex, HttpServletRequest request) {
        log.warn(ExceptionConstants.LOG_FORMAT_SECURITY, ex.getMessage(), request.getHeader(CF_CONNECTING_IP), request.getRequestURI());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Two moves racing on the same turn. The client simply re-reads state, since
     * the server pushes the truth over STOMP anyway. Without this the catch-all
     * would turn an ordinary double-tap into a 500.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(HttpServletRequest request) {
        log.warn("Concurrent modification on {}", request.getRequestURI());
        return buildResponse(HttpStatus.CONFLICT, "Ходът вече беше отигран.", request.getRequestURI());
    }

    /**
     * A move the table has already moved past — 409, not 500.
     *
     * <p>Answered like the optimistic-lock case above and for the same
     * reason: the client re-reads state, which the server is pushing anyway.
     * Logged at info with no stack trace, because it is not a fault; a race
     * between a tap and a socket is the normal weather of a card game, and a
     * log full of them is a log nobody reads when something really breaks.
     */
    @ExceptionHandler(IllegalMoveException.class)
    public ResponseEntity<ErrorResponse> handleIllegalMove(IllegalMoveException ex, HttpServletRequest request) {
        log.info("Move refused on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ExceptionConstants.MOVE_NO_LONGER_LEGAL,
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn(LOG_FORMAT_SECURITY, ex.getMessage(), request.getHeader(CF_CONNECTING_IP), request.getRequestURI());

        return buildResponse(HttpStatus.BAD_REQUEST, INCORRECT_CREDENTIALS_MESSAGE, request.getRequestURI());
    }

    /**
     * A game that is not on offer to whoever asked for it.
     *
     * <p>404, and the body says no more than that. 403 would tell a stranger
     * the game exists and is being kept from them.
     */
    @ExceptionHandler(ServiceNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceNotAvailable(ServiceNotAvailableException ex,
                                                                   HttpServletRequest request) {
        log.info(LogConstants.SERVICE_NOT_AVAILABLE, ex.getCode(),
                AuthenticatedUser.username(), request.getRequestURI());

        return buildResponse(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        log.error(ExceptionConstants.LOG_FORMAT_SECURITY, ex.getMessage(), request.getHeader(CF_CONNECTING_IP), request.getRequestURI());

        return buildResponse(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), request.getRequestURI());
    }

    /**
     * A JWT that cannot be parsed or has expired — 401, not 500.
     *
     * <p>{@code refreshToken} reads the username out of the token before it
     * checks validity, so an expired or tampered refresh token came out of
     * jjwt as a parse failure and fell through to the catch-all below. The
     * client had to recognise a 500 carrying the exception's name to tell a
     * dead session from a server hiccup; the answer is now what it should
     * always have been.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwt(JwtException ex, HttpServletRequest request) {
        log.warn(ExceptionConstants.LOG_FORMAT_SECURITY, ex.getClass().getSimpleName(),
                request.getHeader(CF_CONNECTING_IP), request.getRequestURI());

        return buildResponse(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), request.getRequestURI());
    }

    /**
     * A link from an email that is unknown, already used or expired. That is a
     * bad request about the link — never 401, which the client reads as "your
     * session ended" and answers by sending the person to the login screen
     * instead of the invalid-link page.
     */
    @ExceptionHandler(InvalidLinkException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLink(InvalidLinkException ex, HttpServletRequest request) {
        log.warn(ExceptionConstants.LOG_FORMAT_SECURITY, ex.getMessage(),
                request.getHeader(CF_CONNECTING_IP), request.getRequestURI());

        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({
            UserAlreadyExistsException.class,
            EmailAlreadyExistsException.class,
    })
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(RuntimeException ex, HttpServletRequest request) {
        log.warn(ExceptionConstants.LOG_FORMAT_SECURITY, ex.getMessage(), request.getHeader(CF_CONNECTING_IP), request.getRequestURI());

        return buildResponse(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String validationDetails = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> String.format(VALIDATION_DETAILS_FORMAT, error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(COMMA_DELIMITER));

        log.error(ExceptionConstants.LOG_FORMAT_ERROR,
                ExceptionConstants.VALIDATION_ERROR_TITLE, request.getRequestURI(), validationDetails);

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ExceptionConstants.VALIDATION_ERROR_TITLE,
                validationDetails,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String parameterName = ex.getName();
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : ExceptionConstants.UNKNOWN;

        String message = String.format(ExceptionConstants.PARAMETER_TYPE_MISMATCH, parameterName, requiredType);

        log.error(ExceptionConstants.LOG_FORMAT_ERROR, ExceptionConstants.TYPE_MISMATCH_TITLE, request.getRequestURI(), message, ex);

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ExceptionConstants.TYPE_MISMATCH_TITLE,
                message,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error(ExceptionConstants.LOG_FORMAT_UNHANDLED, request.getHeader(CF_CONNECTING_IP), request.getRequestURI(),
                ex.getClass().getSimpleName(), request.getRequestURI(), ex);

        // The class name stays in the log, where it belongs, and out of the
        // answer: it tells a caller which library failed and where. The one
        // client that read it now gets a 401 from the refresh endpoint instead
        // of a 500 to sift through.
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionConstants.INTERNAL_SERVER_ERROR_MESSAGE,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String validationDetails = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        log.error(ExceptionConstants.LOG_FORMAT_ERROR,
                ExceptionConstants.VALIDATION_ERROR_TITLE,
                request.getRequestURI(),
                validationDetails);

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ExceptionConstants.VALIDATION_ERROR_TITLE,
                validationDetails,
                request.getRequestURI()
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, Object details, String path) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .message(message)
                        .details(details)
                        .path(path)
                        .build()
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String path) {
        return buildResponse(status, message, null, path);
    }
}
