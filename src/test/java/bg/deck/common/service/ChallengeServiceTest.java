package bg.deck.common.service;

import bg.deck.common.enums.FriendEventType;
import bg.deck.common.exception.BadRequestException;
import bg.deck.common.exception.NotFoundException;
import bg.deck.common.model.event.UserDeleted;
import bg.deck.common.model.response.ChallengeAcceptedResponse;
import bg.deck.common.model.response.ChallengeView;
import bg.deck.common.model.response.FriendEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Challenges between friends")
class ChallengeServiceTest {

    private static final UUID GAME_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final FriendshipService friendships = mock(FriendshipService.class);
    private final PresenceService presence = mock(PresenceService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final WebSocketService socket = mock(WebSocketService.class);
    private final ChallengeStarter santase = mock(ChallengeStarter.class);

    private ChallengeService challenges;

    @BeforeEach
    void setUp() {
        when(santase.code()).thenReturn("SANTASE");
        when(santase.startFriendly("petko91", "gosho")).thenReturn(GAME_ID);
        when(friendships.areFriends("petko91", "gosho")).thenReturn(true);
        when(presence.isOnline(any())).thenReturn(true);
        when(presence.playing(any())).thenReturn(Optional.empty());
        challenges = new ChallengeService(friendships, presence, availability, socket, List.of(santase));
    }

    @Test
    @DisplayName("a challenge reaches the friend, and waits on both sides")
    void aChallengeIsSent() {
        ChallengeView challenge = challenges.challenge("petko91", "gosho", "santase");

        assertThat(challenge.game()).isEqualTo("SANTASE");
        assertThat(challenges.incomingFor("gosho")).contains(challenge);
        assertThat(challenges.outgoingFor("petko91")).contains(challenge);
        assertThat(sentTo("gosho").type()).isEqualTo(FriendEventType.CHALLENGE);
    }

    @Test
    @DisplayName("only a friend who is here and not playing can be challenged, one challenge at a time")
    void refusals() {
        assertThrows(BadRequestException.class, () -> challenges.challenge("petko91", "ivan", "SANTASE"));
        assertThrows(BadRequestException.class, () -> challenges.challenge("petko91", "gosho", "CHESS"));

        when(presence.isOnline("gosho")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> challenges.challenge("petko91", "gosho", "SANTASE"));

        when(presence.isOnline("gosho")).thenReturn(true);
        when(presence.playing("gosho")).thenReturn(Optional.of("TABLA"));
        assertThrows(BadRequestException.class, () -> challenges.challenge("petko91", "gosho", "SANTASE"));

        when(presence.playing("gosho")).thenReturn(Optional.empty());
        challenges.challenge("petko91", "gosho", "SANTASE");
        assertThrows(BadRequestException.class, () -> challenges.challenge("petko91", "gosho", "SANTASE"));
    }

    @Test
    @DisplayName("accepting starts a friendly game and tells both where it is")
    void acceptingStartsTheGame() {
        ChallengeView challenge = challenges.challenge("petko91", "gosho", "SANTASE");

        assertThrows(NotFoundException.class, () -> challenges.accept("petko91", challenge.id()));
        ChallengeAcceptedResponse started = challenges.accept("gosho", challenge.id());

        assertThat(started).isEqualTo(new ChallengeAcceptedResponse("SANTASE", GAME_ID));
        verify(santase).startFriendly("petko91", "gosho");
        assertThat(sentTo("petko91").gameId()).isEqualTo(GAME_ID);
        assertThat(challenges.incomingFor("gosho")).isEmpty();
        assertThrows(NotFoundException.class, () -> challenges.accept("gosho", challenge.id()));
    }

    @Test
    @DisplayName("a declined challenge tells the challenger; a cancelled one tells the friend")
    void decliningAndCancelling() {
        ChallengeView first = challenges.challenge("petko91", "gosho", "SANTASE");
        challenges.decline("gosho", first.id());
        assertThat(sentTo("petko91").type()).isEqualTo(FriendEventType.CHALLENGE_DECLINED);

        ChallengeView second = challenges.challenge("petko91", "gosho", "SANTASE");
        assertThrows(NotFoundException.class, () -> challenges.cancel("gosho", second.id()));
        challenges.cancel("petko91", second.id());
        assertThat(lastSentTo("gosho").type()).isEqualTo(FriendEventType.CHALLENGE_CANCELLED);
        verify(santase, never()).startFriendly(any(), any());
    }

    @Test
    @DisplayName("an unanswered challenge runs out, and a deleted account's is dropped")
    void expiryAndDeletion() {
        ChallengeView challenge = challenges.challenge("petko91", "gosho", "SANTASE");
        challenges.expire(challenge.id());
        assertThat(sentTo("petko91").type()).isEqualTo(FriendEventType.CHALLENGE_EXPIRED);
        assertThat(challenges.outgoingFor("petko91")).isEmpty();

        challenges.challenge("petko91", "gosho", "SANTASE");
        challenges.onUserDeleted(new UserDeleted("gosho"));
        assertThat(challenges.outgoingFor("petko91")).isEmpty();
    }

    private FriendEvent sentTo(String username) {
        ArgumentCaptor<FriendEvent> event = ArgumentCaptor.forClass(FriendEvent.class);
        verify(socket, atLeastOnce()).notifyFriends(eq(username), event.capture());
        return event.getAllValues().getFirst();
    }

    private FriendEvent lastSentTo(String username) {
        ArgumentCaptor<FriendEvent> event = ArgumentCaptor.forClass(FriendEvent.class);
        verify(socket, atLeastOnce()).notifyFriends(eq(username), event.capture());
        return event.getAllValues().getLast();
    }
}
