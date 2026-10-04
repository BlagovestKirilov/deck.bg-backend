package bg.deck.common.security;

import bg.deck.common.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

import static bg.deck.common.constant.Constants.BEARER;

/**
 * Authenticates the game socket on the STOMP CONNECT frame.
 *
 * <p>The socket used to be authenticated by the handshake, which meant the
 * token travelled in the URL — {@code /ws-game?token=...} — because a browser
 * cannot set headers on a WebSocket handshake. URLs are written to access
 * logs, the proxy's included, and the token being carried there was the
 * <em>refresh</em> token: the one that mints new sessions. A CONNECT frame is
 * a message, not a URL, so the header can carry the short-lived access token
 * instead and nothing is logged.
 *
 * <p>The handshake principal is still accepted when a CONNECT arrives without
 * the header, so a client running the previous build keeps working through a
 * deploy. Once those are gone, that fallback and the query-parameter branch in
 * {@link JwtAuthenticationFilter} should both go.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorise(accessor);
            default -> { /* the broker's own frames need no check */ }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER)) {
            // The handshake authenticates nobody: the token used to ride in the
            // URL, where every access log kept a copy, and that branch is gone.
            // A CONNECT frame carries its own token or it is refused.
            log.warn("Game socket refused: no bearer token on the CONNECT frame");
            throw new BadCredentialsException("The game socket needs a token.");
        }

        String jwt = header.substring(BEARER.length());

        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (RuntimeException e) {
            // Say what was wrong with it, never what it was. A client that
            // sends something that is not a token at all — the wrong
            // variable, an unawaited promise — reconnects forever, and
            // without this the only trace is a DISCONNECT with no reason.
            log.warn("Game socket refused: the token could not be read ({})",
                    e.getClass().getSimpleName());
            throw new BadCredentialsException("The game socket's token could not be read.", e);
        }

        if (username == null || !jwtService.isTokenValid(jwt)) {
            log.warn("Game socket refused: the token is not valid for {}", username);
            throw new BadCredentialsException("The game socket's token is not valid.");
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(jwtService.extractRole(jwt)))));
    }

    /**
     * A player may only listen to their own topics.
     *
     * <p>Every destination the server publishes to ends with the username it is
     * meant for — {@code /topic/game/{id}/{username}} and the search topics —
     * and each carries that player's own view, their cards included. Nothing
     * stopped a signed-in player from subscribing to someone else's.
     */
    private void authorise(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        String destination = accessor.getDestination();

        if (user == null) {
            throw new BadCredentialsException("Not connected.");
        }
        if (destination == null || !destination.endsWith("/" + user.getName())) {
            log.warn("{} tried to subscribe to {}", user.getName(), destination);
            throw new AccessDeniedException("That topic belongs to another player.");
        }
    }
}
