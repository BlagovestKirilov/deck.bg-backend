package bg.deck.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is here, for the friends list.
 *
 * <p>A player is online while at least one of their screens holds a game
 * socket open: the hub keeps one for the friends topic, and every game screen
 * has its own. Kept in memory, per open socket, and forgotten when it closes.
 * Which game they are in, if any, each game says for itself through a
 * {@link PlayingProvider}.
 */
@RequiredArgsConstructor
@Service
public class PresenceService {

    private final List<PlayingProvider> games;

    /** Each open socket and the player it belongs to. */
    private final Map<String, String> sockets = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String socket = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user != null && socket != null) {
            sockets.put(socket, user.getName());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        sockets.remove(event.getSessionId());
    }

    /** True while the player has a screen open. */
    public boolean isOnline(String username) {
        return sockets.containsValue(username);
    }

    /** The code of the game the player is in now, if any. */
    public Optional<String> playing(String username) {
        return games.stream()
                .filter(game -> game.isPlaying(username))
                .map(PlayingProvider::code)
                .findFirst();
    }
}
