package bg.deck.common.service;

import bg.deck.common.enums.FriendEventType;
import bg.deck.common.model.dto.FriendLinks;
import bg.deck.common.model.event.PlayingChanged;
import bg.deck.common.model.response.FriendEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A game starting or ending reaches the players' friends, not only a socket opening. */
class PresenceServiceTest {

    private final FriendshipService friendships = mock(FriendshipService.class);
    private final WebSocketService sockets = mock(WebSocketService.class);
    private final PresenceService presence = new PresenceService(List.of(), friendships, sockets);

    @Test
    void aGameStartingOrEndingTellsEachPlayersFriends() {
        when(friendships.linksOf("ivan")).thenReturn(new FriendLinks(List.of("maria", "petar"), List.of(), List.of()));
        when(friendships.linksOf("georgi")).thenReturn(new FriendLinks(List.of(), List.of(), List.of()));

        presence.onPlayingChanged(new PlayingChanged(List.of("ivan", "georgi")));

        verify(sockets).notifyFriends(eq("maria"), argThat(isPresenceOf("ivan")));
        verify(sockets).notifyFriends(eq("petar"), argThat(isPresenceOf("ivan")));
        verify(sockets, never()).notifyFriends(eq("ivan"), any());
    }

    @Test
    void aFailureToTellIsNotThrown() {
        when(friendships.linksOf("ivan")).thenThrow(new IllegalStateException("db down"));

        presence.onPlayingChanged(new PlayingChanged(List.of("ivan")));

        verify(sockets, never()).notifyFriends(any(), any());
    }

    private static ArgumentMatcher<FriendEvent> isPresenceOf(String username) {
        return event -> event.type() == FriendEventType.PRESENCE && username.equals(event.from());
    }
}
