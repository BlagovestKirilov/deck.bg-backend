package bg.deck.common.service;

import bg.deck.common.constant.ExceptionConstants;
import bg.deck.common.enums.FriendEventType;
import bg.deck.common.exception.BadRequestException;
import bg.deck.common.exception.NotFoundException;
import bg.deck.common.model.event.UserDeleted;
import bg.deck.common.model.response.ChallengeAcceptedResponse;
import bg.deck.common.model.response.ChallengeView;
import bg.deck.common.model.response.FriendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Challenges between friends: one friend invites another to a game, and an
 * accepted challenge starts it — a game that counts for neither.
 *
 * <p>Held in memory: a challenge lives a minute at most, and one lost to a
 * restart is simply asked again. A player has at most one open, either way,
 * so a screen never has two to answer.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class ChallengeService {

    /** How long a challenge waits for its answer. */
    static final Duration TIME_TO_ANSWER = Duration.ofSeconds(60);

    private final FriendshipService friendshipService;
    private final PresenceService presenceService;
    private final AvailabilityService availabilityService;
    private final WebSocketService webSocketService;
    private final List<ChallengeStarter> starters;

    private final Map<UUID, ChallengeView> open = new ConcurrentHashMap<>();

    /** Challenges a friend to a game; tells them on their friends topic. */
    public ChallengeView challenge(String from, String to, String game) {
        String code = game.trim().toUpperCase();
        starterFor(code);
        if (from.equals(to) || !friendshipService.areFriends(from, to)) {
            throw new BadRequestException(ExceptionConstants.CHALLENGE_NOT_FRIENDS);
        }
        availabilityService.requireAvailable(code, from);
        availabilityService.requireAvailable(code, to);
        if (presenceService.playing(from).isPresent()) {
            throw new BadRequestException(ExceptionConstants.CHALLENGE_YOU_ARE_PLAYING);
        }
        if (!presenceService.isOnline(to)) {
            throw new BadRequestException(String.format(ExceptionConstants.CHALLENGE_OFFLINE, to));
        }
        if (presenceService.playing(to).isPresent()) {
            throw new BadRequestException(String.format(ExceptionConstants.CHALLENGE_BUSY, to));
        }
        if (involved(from).isPresent() || involved(to).isPresent()) {
            throw new BadRequestException(ExceptionConstants.CHALLENGE_ALREADY_OPEN);
        }

        ChallengeView challenge = new ChallengeView(UUID.randomUUID(), from, to, code,
                Instant.now().plus(TIME_TO_ANSWER));
        open.put(challenge.id(), challenge);
        log.info("{} challenged {} to {}", from, to, code);
        webSocketService.notifyFriends(to, FriendEvent.about(FriendEventType.CHALLENGE, challenge, from));
        CompletableFuture.delayedExecutor(TIME_TO_ANSWER.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> expire(challenge.id()));
        return challenge;
    }

    /** Accepts a challenge made to this player, and starts the game. */
    public ChallengeAcceptedResponse accept(String me, UUID id) {
        ChallengeView challenge = take(id, me, true);
        try {
            UUID gameId = starterFor(challenge.game()).startFriendly(challenge.from(), challenge.to());
            log.info("{} accepted {}'s challenge to {}: game {}", me, challenge.from(), challenge.game(), gameId);
            FriendEvent started = FriendEvent.started(challenge.game(), gameId, me);
            webSocketService.notifyFriends(challenge.from(), started);
            webSocketService.notifyFriends(challenge.to(), started);
            return new ChallengeAcceptedResponse(challenge.game(), gameId);
        } catch (RuntimeException e) {
            // The game could not start — one of them sat down somewhere else in
            // the meantime. The challenger is not left waiting for it.
            webSocketService.notifyFriends(challenge.from(),
                    FriendEvent.about(FriendEventType.CHALLENGE_CANCELLED, challenge, me));
            throw e;
        }
    }

    /** Turns down a challenge made to this player. */
    public void decline(String me, UUID id) {
        ChallengeView challenge = take(id, me, true);
        webSocketService.notifyFriends(challenge.from(),
                FriendEvent.about(FriendEventType.CHALLENGE_DECLINED, challenge, me));
    }

    /** Takes back a challenge this player made. */
    public void cancel(String me, UUID id) {
        ChallengeView challenge = take(id, me, false);
        webSocketService.notifyFriends(challenge.to(),
                FriendEvent.about(FriendEventType.CHALLENGE_CANCELLED, challenge, me));
    }

    /** The challenge waiting for this player's answer, if any. */
    public Optional<ChallengeView> incomingFor(String username) {
        return open.values().stream().filter(challenge -> challenge.to().equals(username)).findFirst();
    }

    /** The challenge this player made that is waiting for an answer, if any. */
    public Optional<ChallengeView> outgoingFor(String username) {
        return open.values().stream().filter(challenge -> challenge.from().equals(username)).findFirst();
    }

    /** Forgets a deleted account's challenges, telling whoever was on the other side. */
    @EventListener
    public void onUserDeleted(UserDeleted event) {
        involved(event.username()).ifPresent(challenge -> {
            open.remove(challenge.id());
            String other = challenge.from().equals(event.username()) ? challenge.to() : challenge.from();
            webSocketService.notifyFriends(other,
                    FriendEvent.about(FriendEventType.CHALLENGE_CANCELLED, challenge, event.username()));
        });
    }

    void expire(UUID id) {
        ChallengeView challenge = open.remove(id);
        if (challenge == null) {
            return;
        }
        FriendEvent expired = FriendEvent.about(FriendEventType.CHALLENGE_EXPIRED, challenge, challenge.to());
        webSocketService.notifyFriends(challenge.from(), expired);
        webSocketService.notifyFriends(challenge.to(), expired);
    }

    private Optional<ChallengeView> involved(String username) {
        return open.values().stream()
                .filter(challenge -> challenge.from().equals(username) || challenge.to().equals(username))
                .findFirst();
    }

    /** Removes the challenge, if it is this player's to answer (or, to take back, theirs). */
    private ChallengeView take(UUID id, String me, boolean toMe) {
        ChallengeView challenge = open.get(id);
        if (challenge == null || !(toMe ? challenge.to() : challenge.from()).equals(me)
                || !open.remove(id, challenge)) {
            throw new NotFoundException(ExceptionConstants.CHALLENGE_GONE);
        }
        return challenge;
    }

    private ChallengeStarter starterFor(String code) {
        return starters.stream()
                .filter(starter -> starter.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(ExceptionConstants.CHALLENGE_GAME_UNKNOWN));
    }
}
