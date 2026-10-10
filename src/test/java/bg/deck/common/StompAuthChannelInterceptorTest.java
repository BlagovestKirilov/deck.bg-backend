package bg.deck.common;

import bg.deck.common.security.StompAuthChannelInterceptor;
import bg.deck.common.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The game socket's authentication, which moved off the handshake so the token
 * would stop travelling in the URL — and with it, into every access log.
 */
class StompAuthChannelInterceptorTest {

    private static final String VALID = "valid.jwt.token";
    private static final String EXPIRED = "expired.jwt.token";

    private JwtService jwtService;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        interceptor = new StompAuthChannelInterceptor(jwtService);

        when(jwtService.extractUsername(VALID)).thenReturn("petko91");
        when(jwtService.extractRole(VALID)).thenReturn("ROLE_USER");
        when(jwtService.isTokenValid(VALID)).thenReturn(true);

        when(jwtService.extractUsername(EXPIRED)).thenReturn("petko91");
        when(jwtService.isTokenValid(EXPIRED)).thenReturn(false);
    }

    private Message<byte[]> frame(StompCommand command, String authorization, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal connected() {
        return new UsernamePasswordAuthenticationToken("petko91", null, java.util.List.of());
    }

    @Nested
    @DisplayName("CONNECT")
    class Connect {

        @Test
        void tokenInTheHeaderIdentifiesThePlayer() {
            Message<byte[]> message = frame(StompCommand.CONNECT, "Bearer " + VALID, null, null);

            interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));

            Principal user = StompHeaderAccessor.wrap(message).getUser();
            assertNotNull(user);
            assertEquals("petko91", user.getName());
        }

        @Test
        void expiredTokenIsRefused() {
            Message<byte[]> message = frame(StompCommand.CONNECT, "Bearer " + EXPIRED, null, null);

            assertThrows(BadCredentialsException.class,
                    () -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
        }

        @Test
        void noTokenAndNoHandshakeIsRefused() {
            Message<byte[]> message = frame(StompCommand.CONNECT, null, null, null);

            assertThrows(BadCredentialsException.class,
                    () -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
        }

        /**
         * The handshake authenticates nobody any more. It used to, from a token
         * in the URL, which is how access tokens ended up in the access logs —
         * so a CONNECT without a token of its own is refused, principal or not.
         */
        @Test
        void aHandshakePrincipalIsNotEnoughWithoutAToken() {
            Message<byte[]> message = frame(StompCommand.CONNECT, null, null, connected());

            assertThrows(BadCredentialsException.class,
                    () -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
        }
    }

    @Nested
    @DisplayName("SUBSCRIBE")
    class Subscribe {

        @Test
        void ownTopicIsAllowed() {
            Message<byte[]> message = frame(
                    StompCommand.SUBSCRIBE, null, "/topic/game/abc-123/petko91", connected());

            interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
        }

        /**
         * Every game topic carries that player's own view — their cards, their
         * checkers. Nothing used to stop a signed-in player from listening to
         * somebody else's.
         */
        @Test
        void anotherPlayersTopicIsRefused() {
            Message<byte[]> message = frame(
                    StompCommand.SUBSCRIBE, null, "/topic/game/abc-123/ninja2011", connected());

            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
        }

        @Test
        void aNameThatMerelyEndsTheSameIsRefused() {
            Message<byte[]> message = frame(
                    StompCommand.SUBSCRIBE, null, "/topic/game/abc-123/notpetko91", connected());

            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
        }

        @Test
        void searchTopicIsAllowed() {
            Message<byte[]> message = frame(
                    StompCommand.SUBSCRIBE, null, "/topic/search/tabla/petko91", connected());

            interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
        }
    }
}
