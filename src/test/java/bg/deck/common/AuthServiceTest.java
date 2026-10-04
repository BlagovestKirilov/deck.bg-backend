package bg.deck.common;

import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.exception.InvalidTokenException;
import bg.deck.common.exception.UserAlreadyExistsException;
import bg.deck.common.enums.GameType;
import bg.deck.common.model.User;
import bg.deck.common.model.request.LoginRequest;
import bg.deck.common.model.request.RegisterRequest;
import bg.deck.common.model.response.AuthResponse;
import bg.deck.common.service.AuthService;
import bg.deck.common.service.EmailConfirmationService;
import bg.deck.common.service.ForgotPasswordService;
import bg.deck.common.service.UserAccountService;
import bg.deck.common.service.EmailService;
import bg.deck.common.service.JwtService;
import bg.deck.common.util.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static bg.deck.common.constant.Constants.CF_CONNECTING_IP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final String username = "testUser";
    private final String password = "password123";
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private ForgotPasswordService forgotPasswordService;
    @Mock
    private EmailConfirmationService emailConfirmationService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtService jwtService;
    @Mock
    private HttpServletRequest httpServletRequest;
    @InjectMocks
    private AuthService authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername(username);
        testUser.setPassword("encodedPassword");
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {
        @Test
        void login_Success() {
            // given
            LoginRequest request = new LoginRequest(username, password);

            when(userAccountService.findByUsername(username))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(password, testUser.getPassword()))
                    .thenReturn(true);
            when(jwtService.generateToken(testUser))
                    .thenReturn("access-token");
            when(jwtService.generateRefreshToken(testUser))
                    .thenReturn("refresh-token");
            when(httpServletRequest.getHeader(CF_CONNECTING_IP))
                    .thenReturn("127.0.0.1");

            // when
            AuthResponse response = authService.login(request, httpServletRequest);

            // then
            assertNotNull(response);
            assertEquals(HttpStatus.OK.getReasonPhrase(), response.status());
            assertEquals("access-token", response.token());
            assertEquals("refresh-token", response.refreshToken());
            assertEquals("127.0.0.1", testUser.getIpAddress());

            verify(userAccountService).findByUsername(username);
            verify(userAccountService).save(testUser);
            verify(jwtService).generateToken(testUser);
            verify(jwtService).generateRefreshToken(testUser);
        }

        @Test
        void login_InvalidCredentials_ThrowsException() {
            // given
            LoginRequest request = new LoginRequest(username, "wrong-password");

            when(userAccountService.findByUsername(username))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrong-password", testUser.getPassword()))
                    .thenReturn(false);

            // then
            assertThrows(
                    InvalidCredentialsException.class,
                    () -> authService.login(request, httpServletRequest)
            );

            verify(userAccountService).findByUsername(username);
            verify(userAccountService, never()).save(any());
            verify(jwtService, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {
        @Test
        void register_Success() {
            RegisterRequest request = new RegisterRequest(username, password, "test@example.com");

            testUser.setPassword(password);
            when(userAccountService.existsByUsername(username)).thenReturn(false);
            when(userAccountService.existsByEmail("test@example.com")).thenReturn(false);
            when(userMapper.toEntity(request)).thenReturn(testUser);
            when(passwordEncoder.encode(password)).thenReturn("encodedPassword");

            AuthResponse response = authService.register(request);

            assertNotNull(response);
            assertEquals(HttpStatus.OK.getReasonPhrase(), response.status());
            assertEquals("encodedPassword", testUser.getPassword());
            verify(userAccountService, atLeastOnce()).save(testUser);

            // One stats row per game type is created up front.
            assertNotNull(testUser.statsFor(GameType.SANTASE));
            assertNotNull(testUser.statsFor(GameType.TABLA));

            // A pending confirmation is stored for the new user and emailed.
            ArgumentCaptor<User> confirmed = ArgumentCaptor.forClass(User.class);
            verify(emailConfirmationService).issueFor(confirmed.capture());
            assertEquals(testUser, confirmed.getValue());
            verify(emailService).sendConfirmationEmail(any());

        }

        @Test
        void register_UserExists_ThrowsException() {
            RegisterRequest request = new RegisterRequest(username, null, null);

            when(userAccountService.existsByUsername(username)).thenReturn(true);

            assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
            verify(userAccountService, never()).save(any());
            verifyNoInteractions(emailConfirmationService, emailService);
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {
        @Test
        void refreshToken_Success() {
            String oldRefreshToken = "valid-refresh-token";
            when(jwtService.extractUsername(oldRefreshToken)).thenReturn(username);
            when(userAccountService.findByUsername(username)).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid(oldRefreshToken)).thenReturn(true);
            when(jwtService.generateToken(testUser)).thenReturn("new-access-token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");

            AuthResponse response = authService.refreshToken(oldRefreshToken);

            assertEquals("new-access-token", response.token());
            assertEquals("new-refresh-token", response.refreshToken());
        }

        @Test
        void refreshToken_InvalidToken_ThrowsException() {
            String invalidToken = "invalid-token";
            when(jwtService.extractUsername(invalidToken)).thenReturn(username);
            when(userAccountService.findByUsername(username)).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid(invalidToken)).thenReturn(false);

            assertThrows(InvalidTokenException.class, () -> authService.refreshToken(invalidToken));
        }
    }
}
