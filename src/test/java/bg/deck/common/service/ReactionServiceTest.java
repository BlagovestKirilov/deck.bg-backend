package bg.deck.common.service;

import bg.deck.common.enums.Reaction;
import bg.deck.common.model.response.ReactionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Reactions")
class ReactionServiceTest {

    private final WebSocketService webSocketService = mock(WebSocketService.class);
    private final ReactionService reactions = new ReactionService(webSocketService);

    @Test
    @DisplayName("go to everyone at the table, the sender too")
    void everyoneHearsIt() {
        assertTrue(reactions.send("petko91", List.of("petko91", "ivan"), Reaction.RED_CARD));

        ReactionResponse said = new ReactionResponse("petko91", Reaction.RED_CARD);
        verify(webSocketService).notifyReaction("petko91", said);
        verify(webSocketService).notifyReaction("ivan", said);
    }

    @Test
    @DisplayName("one too soon after the last is dropped, and nobody is sent it")
    void tooSoonIsDropped() {
        reactions.send("petko91", List.of("ivan"), Reaction.LAUGH);

        assertFalse(reactions.send("petko91", List.of("ivan"), Reaction.ANGRY));
        verify(webSocketService, never()).notifyReaction("ivan", new ReactionResponse("petko91", Reaction.ANGRY));
    }

    @Test
    @DisplayName("spaced out, a burst is allowed and the next is not until the window moves on")
    void aBurstThenAPause() {
        long now = 0;
        for (int i = 0; i < ReactionService.BURST; i++) {
            assertTrue(reactions.allowed("petko91", now), "reaction " + (i + 1) + " of the burst");
            now += ReactionService.GAP_MS;
        }
        assertFalse(reactions.allowed("petko91", now), "one more inside the window");
        assertTrue(reactions.allowed("petko91", ReactionService.WINDOW_MS + 1), "the first has left the window");
    }

    @Test
    @DisplayName("one player's limit is theirs alone")
    void perPlayer() {
        assertTrue(reactions.allowed("petko91", 0));
        assertTrue(reactions.allowed("ivan", 0));
    }
}
