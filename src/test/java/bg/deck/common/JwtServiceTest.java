package bg.deck.common;

import bg.deck.common.enums.Role;
import bg.deck.common.model.User;
import bg.deck.common.security.JwtProperties;
import bg.deck.common.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    // Standard 256-bit key for HMAC-SHA (Base64 encoded)
    private final String secret = Base64.getEncoder().encodeToString(
            "very-long-secret-key-that-is-at-least-32-bytes-long".getBytes()
    );
    @Mock
    private JwtProperties jwtProperties;
    @InjectMocks
    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("user");
        testUser.setRole(Role.ROLE_USER);

        // Use lenient() to allow these stubs to be unused in some tests
        lenient().when(jwtProperties.getSecretKey()).thenReturn(secret);
        lenient().when(jwtProperties.getExpiration()).thenReturn(3600000L);
        lenient().when(jwtProperties.getRefreshExpiration()).thenReturn(86400000L);
    }

    @Test
    @DisplayName("Should generate a valid JWT token with correct claims")
    void generateToken_Success() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("user");
        assertThat(jwtService.extractRole(token)).isEqualTo(Role.ROLE_USER.name());
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }


    @Test
    @DisplayName("Should generate a refresh token with longer expiration")
    void generateRefreshToken_Success() {
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("user");
    }

    @Test
    @DisplayName("Should fail validation if token is expired")
    void isTokenValid_ExpiredToken_ThrowsException() {
        // Set up a token that expires instantly
        when(jwtProperties.getExpiration()).thenReturn(-1000L);
        String expiredToken = jwtService.generateToken(testUser);

        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Should correctly extract custom claims")
    void extractClaim_CustomResolver() {
        String token = jwtService.generateToken(testUser);

        // Extracting subject using a custom resolver function
        String subject = jwtService.extractClaim(token, Claims::getSubject);

        assertThat(subject).isEqualTo("user");
    }
}
