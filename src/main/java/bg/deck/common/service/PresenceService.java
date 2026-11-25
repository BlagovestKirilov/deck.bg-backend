package bg.deck.common.service;

import bg.deck.common.enums.FriendEventType;
import bg.deck.common.model.event.PlayingChanged;
import bg.deck.common.model.response.FriendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
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
@Log4j2
@RequiredArgsConstructor
@Service
public class PresenceService {

    private final List<PlayingProvider> games;
    private final FriendshipService friendshipService;
    private final WebSocketService webSocketService;

    /** Each open socket and the player it belongs to. */
    private final Map<String, String> sockets = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String socket = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user != null && socket != null) {
            sockets.put(socket, user.getName());
            tellFriends(user.getName());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        String username = sockets.remove(event.getSessionId());
        if (username != null) {
            tellFriends(username);
        }
    }

    /**
     * A game started or ended. Heard after it is committed, so a friend who
     * reads the list then sees it; or at once when there was no transaction.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onPlayingChanged(PlayingChanged event) {
        event.usernames().forEach(this::tellFriends);
    }

    /**
     * Tells this player's friends that their "here" or "playing" may have
     * changed, so their lists are read again — on every socket that opens or
     * closes, since going to a table closes the hub's and opens the game's.
     * A failure to tell is logged, never thrown: a socket's lifecycle is not
     * to be stopped by a friends list.
     */
    private void tellFriends(String username) {
        try {
            FriendEvent changed = FriendEvent.of(FriendEventType.PRESENCE, username);
            friendshipService.linksOf(username).friends()
                    .forEach(friend -> webSocketService.notifyFriends(friend, changed));
        } catch (RuntimeException e) {
            log.warn("Could not tell {}'s friends their presence changed", username, e);
        }
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
