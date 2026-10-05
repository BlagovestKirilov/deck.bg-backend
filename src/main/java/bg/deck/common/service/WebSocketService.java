package bg.deck.common.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.response.SearchGameResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static bg.deck.common.constant.Constants.NOTIFY_BELOT_DESTINATION;
import static bg.deck.common.constant.Constants.NOTIFY_GAME_DESTINATION;
import static bg.deck.common.constant.Constants.NOTIFY_GAME_SEARCH_DESTINATION;
import static bg.deck.common.constant.Constants.NOTIFY_SEARCH_BY_GAME_DESTINATION;

/**
 * Sends each player what changed, a message at a time and in order.
 *
 * <p>Not {@code @Async}. Every send already leaves the caller at once: it
 * takes a slot in that player's queue and is handed to a virtual thread,
 * which turns the state into JSON and sends it. {@code @Async} on top found
 * no executor to use, so Spring started a new platform thread for every call
 * — four for every belot move — and two updates for one player then raced
 * each other to the queue, so the older could take the earlier slot and
 * reach the screen last. Called directly, the queue keeps the order the
 * table changed in.
 */
@RequiredArgsConstructor
@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConcurrentMap<String, AtomicLong> userNextAvailableTime = new ConcurrentHashMap<>();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final long MIN_DELAY_MS = 50;

    /**
     * The game topic is keyed by game id, so both games share it safely and no
     * client-side change is needed for the destination.
     */
    public void notifyGameUpdate(String gameId, String username, Object gameState) {
        String destination = String.format(NOTIFY_GAME_DESTINATION, gameId, username);
        enqueueMessage(username, () -> messagingTemplate.convertAndSend(destination, gameState));
    }

    /** One seat’s view of a belot table. Never the table’s view: it holds a hand. */
    public void notifyBelotUpdate(String username, Object state) {
        String destination = String.format(NOTIFY_BELOT_DESTINATION, username);
        enqueueMessage(username, () -> messagingTemplate.convertAndSend(destination, state));
    }

    public void notifyGameSearch(String username, SearchGameResponse searchGameResponse) {
        String destination = String.format(NOTIFY_GAME_SEARCH_DESTINATION, username);
        enqueueMessage(username, () -> messagingTemplate.convertAndSend(destination, searchGameResponse));
    }

    /**
     * Publishes to the game-scoped search topic and, for one release, to the
     * legacy un-scoped one as well so a client running the old build during a
     * rolling deploy still receives its match.
     */
    public void notifyGameSearch(String username, GameType gameType, SearchGameResponse response) {
        String scoped = String.format(NOTIFY_SEARCH_BY_GAME_DESTINATION,
                gameType.name().toLowerCase(), username);
        String legacy = String.format(NOTIFY_GAME_SEARCH_DESTINATION, username);
        enqueueMessage(username, () -> {
            messagingTemplate.convertAndSend(scoped, response);
            messagingTemplate.convertAndSend(legacy, response);
        });
    }

    public void notifyGameSearch(List<String> usernames, GameType gameType, SearchGameResponse response) {
        usernames.forEach(username -> notifyGameSearch(username, gameType, response));
    }

    public void notifyGameSearch(List<String> usernames, SearchGameResponse searchGameResponse) {
        for (String username : usernames) {
            String destination = String.format(NOTIFY_GAME_SEARCH_DESTINATION, username);
            messagingTemplate.convertAndSend(destination, searchGameResponse);
        }
    }

    private void enqueueMessage(String username, Runnable task) {
        long now = System.currentTimeMillis();

        AtomicLong nextSlot = userNextAvailableTime.computeIfAbsent(username, _ -> new AtomicLong(0));

        long scheduledTime;
        synchronized (nextSlot) {
            scheduledTime = Math.max(now, nextSlot.get());
            nextSlot.set(scheduledTime + MIN_DELAY_MS);
        }

        long delay = scheduledTime - now;

        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                if (nextSlot.get() <= System.currentTimeMillis()) {
                    userNextAvailableTime.remove(username, nextSlot);
                }
            }
        };

        if (delay <= 0) {
            virtualExecutor.execute(wrappedTask);
        } else {
            CompletableFuture
                    .delayedExecutor(delay, TimeUnit.MILLISECONDS, virtualExecutor)
                    .execute(wrappedTask);
        }
    }

    @PreDestroy
    public void shutdown() {
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
            }
        } catch (InterruptedException _) {
            virtualExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}