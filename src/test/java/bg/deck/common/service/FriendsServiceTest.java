package bg.deck.common.service;

import bg.deck.common.enums.FriendEventType;
import bg.deck.common.model.dto.FriendLinks;
import bg.deck.common.model.response.FriendEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A change made from one screen reaches the player's other screens. */
class FriendsServiceTest {

    private final FriendshipService friendships = mock(FriendshipService.class);
    private final WebSocketService sockets = mock(WebSocketService.class);
    private final FriendsService friends = new FriendsService(friendships, mock(ChallengeService.class),
            mock(PresenceService.class), List.of(), sockets);

    @Test
    void aChangeTellsThePlayersOwnOtherScreens() {
        when(friendships.linksOf("ivan")).thenReturn(new FriendLinks(List.of(), List.of(), List.of()));

        assertThat(friends.changedBy("ivan")).isNotNull();

        verify(sockets).notifyFriends(eq("ivan"),
                argThat((FriendEvent event) -> event.type() == FriendEventType.SYNC));
    }

    @Test
    void readingThePageTellsNobody() {
        when(friendships.linksOf("ivan")).thenReturn(new FriendLinks(List.of(), List.of(), List.of()));

        friends.pageOf("ivan");

        verify(sockets, never()).notifyFriends(any(), any());
    }
}
