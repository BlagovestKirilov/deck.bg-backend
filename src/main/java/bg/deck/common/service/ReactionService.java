package bg.deck.common.service;

import bg.deck.common.enums.Reaction;
import bg.deck.common.model.response.ReactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands a reaction to everyone at a table, and keeps one player from
 * flooding it.
 *
 * <p>Nothing is stored: a reaction is a moment at the table, gone when its
 * bubble fades. Which table, and who sits at it, is the game's to say — this
 * class knows no game, so {@code common} still imports none.
 *
 * <p>The limit is kept in memory, per player: at most one reaction every
 * {@link #GAP_MS} and {@link #BURST} in any {@link #WINDOW_MS}. One more is
 * dropped quietly rather than refused — a player tapping too fast sees their
 * last one not appear, which is answer enough.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class ReactionService {

    /** The shortest time between two reactions from one player. */
    static final long GAP_MS = 1_500;
    /** How many fit in {@link #WINDOW_MS}. */
    static final int BURST = 6;
    static final long WINDOW_MS = 30_000;

    private final WebSocketService webSocketService;
    private final Map<String, Deque<Long>> sent = new ConcurrentHashMap<>();

    /**
     * Sends it to everyone at the sender's table, the sender too.
     *
     * @param table everyone seated at it, by username
     * @return false when it was dropped for coming too soon
     */
    public boolean send(String from, Collection<String> table, Reaction reaction) {
        if (!allowed(from, System.currentTimeMillis())) {
            log.debug("Reaction from {} dropped: too many too fast", from);
            return false;
        }
        ReactionResponse said = new ReactionResponse(from, reaction);
        table.forEach(username -> webSocketService.notifyReaction(username, said));
        return true;
    }

    /** Whether this player may send one now; counts it if so. */
    boolean allowed(String from, long now) {
        Deque<Long> times = sent.computeIfAbsent(from, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (!times.isEmpty() && now - times.peekLast() < GAP_MS) {
                return false;
            }
            if (times.size() >= BURST) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
