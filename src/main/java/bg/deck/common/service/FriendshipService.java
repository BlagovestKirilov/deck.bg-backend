package bg.deck.common.service;

import bg.deck.common.constant.ExceptionConstants;
import bg.deck.common.enums.FriendEventType;
import bg.deck.common.enums.FriendshipStatus;
import bg.deck.common.exception.BadRequestException;
import bg.deck.common.exception.NotFoundException;
import bg.deck.common.model.Friendship;
import bg.deck.common.model.User;
import bg.deck.common.model.dto.FriendLinks;
import bg.deck.common.model.response.FriendEvent;
import bg.deck.common.model.response.FriendRequestView;
import bg.deck.common.enums.FriendRelation;
import bg.deck.common.model.response.FriendSearchHit;
import bg.deck.common.model.response.FriendSearchResponse;
import bg.deck.common.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Friendships: asking, answering, ending — the only place
 * {@link FriendshipRepository} is spoken to.
 *
 * <p>A request is one row, PENDING until the other player accepts it. Asking
 * somebody who has already asked you accepts theirs: the two of you want the
 * same thing. Nothing is deleted: a request turned down is REJECTED, one taken
 * back CANCELLED, an ended friendship REMOVED — the row stays as the record of
 * it, and is reused when either asks again. Each change is told to the other
 * player's friends topic once it is committed.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class FriendshipService {

    /** How many names a search shows: no more than fit the panel without scrolling. */
    static final int SEARCH_LIMIT = 3;
    /** The fewest letters a search starts at: fewer would list half the players. */
    static final int SEARCH_MIN_LENGTH = 3;
    /**
     * How long a player whose request was turned down waits to ask the same
     * player again. The one who said no can ask at any time — they may have
     * changed their mind; the one told no cannot keep asking.
     */
    static final Duration ASK_AGAIN_AFTER_REJECTION = Duration.ofDays(7);

    private final FriendshipRepository friendshipRepository;
    private final UserAccountService userAccountService;
    private final WebSocketService webSocketService;

    /** This player's friends and the requests either way. */
    @Transactional(readOnly = true)
    public FriendLinks linksOf(String me) {
        List<String> friends = new ArrayList<>();
        List<FriendRequestView> incoming = new ArrayList<>();
        List<FriendRequestView> outgoing = new ArrayList<>();
        for (Friendship link : friendshipRepository.findAllOf(me)) {
            String other = link.otherThan(me).getUsername();
            if (link.getStatus() == FriendshipStatus.ACCEPTED) {
                friends.add(other);
            } else if (link.getStatus() != FriendshipStatus.PENDING) {
                // Turned down, taken back or ended: a record, not a link.
                continue;
            } else if (link.getAddressee().getUsername().equals(me)) {
                incoming.add(new FriendRequestView(link.getId(), other));
            } else {
                outgoing.add(new FriendRequestView(link.getId(), other));
            }
        }
        friends.sort(String.CASE_INSENSITIVE_ORDER);
        Comparator<FriendRequestView> byName = Comparator.comparing(FriendRequestView::username,
                String.CASE_INSENSITIVE_ORDER);
        incoming.sort(byName);
        outgoing.sort(byName);
        return new FriendLinks(friends, incoming, outgoing);
    }

    /**
     * Asks a player to be friends, by their username in any case. Accepts
     * their request instead where they have already asked.
     */
    @Transactional
    public void request(String me, String username) {
        User target = userAccountService.findByUsernameAnyCase(username.trim())
                .orElseThrow(() -> new NotFoundException(ExceptionConstants.FRIEND_NOT_FOUND));
        if (target.getUsername().equals(me)) {
            throw new BadRequestException(ExceptionConstants.FRIEND_SELF);
        }
        User self = userAccountService.requireByUsername(me);

        Friendship existing = friendshipRepository.findBetween(self, target).orElse(null);
        if (existing != null && existing.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new BadRequestException(ExceptionConstants.FRIEND_ALREADY);
        }
        if (existing != null && existing.getStatus() == FriendshipStatus.PENDING) {
            if (existing.getRequester().getUsername().equals(me)) {
                throw new BadRequestException(ExceptionConstants.FRIEND_ALREADY_ASKED);
            }
            accept(existing);
            return;
        }

        if (existing != null && existing.getStatus() == FriendshipStatus.REJECTED
                && existing.getRequester().getUsername().equals(me)
                && existing.getRespondedAt() != null
                && existing.getRespondedAt().plus(ASK_AGAIN_AFTER_REJECTION).isAfter(Instant.now())) {
            long days = Math.max(1, Duration.between(Instant.now(),
                    existing.getRespondedAt().plus(ASK_AGAIN_AFTER_REJECTION)).toDays() + 1);
            throw new BadRequestException(String.format(ExceptionConstants.FRIEND_ASKED_TOO_SOON,
                    target.getUsername(), days));
        }

        if (existing != null) {
            // Turned down, taken back or ended before: the pair keeps one row,
            // asked afresh, in whichever direction it is asked now.
            existing.askAgain(self, target);
            friendshipRepository.save(existing);
        } else {
            friendshipRepository.save(new Friendship(self, target));
        }
        log.info("{} asked {} to be friends", me, target.getUsername());
        tell(target.getUsername(), FriendEvent.of(FriendEventType.REQUEST, me));
    }

    /** Accepts a request made to this player. */
    @Transactional
    public void accept(String me, UUID id) {
        Friendship request = friendshipRepository.findById(id)
                .filter(link -> link.getStatus() == FriendshipStatus.PENDING)
                .filter(link -> link.getAddressee().getUsername().equals(me))
                .orElseThrow(() -> new NotFoundException(ExceptionConstants.FRIEND_REQUEST_GONE));
        accept(request);
    }

    /**
     * Turns down a request made to this player (REJECTED), or takes back one
     * they made (CANCELLED). The row is kept.
     */
    @Transactional
    public void decline(String me, UUID id) {
        Friendship request = friendshipRepository.findById(id)
                .filter(link -> link.getStatus() == FriendshipStatus.PENDING)
                .filter(link -> link.getAddressee().getUsername().equals(me)
                        || link.getRequester().getUsername().equals(me))
                .orElseThrow(() -> new NotFoundException(ExceptionConstants.FRIEND_REQUEST_GONE));
        boolean askedMe = request.getAddressee().getUsername().equals(me);
        request.end(askedMe ? FriendshipStatus.REJECTED : FriendshipStatus.CANCELLED);
        friendshipRepository.save(request);
        log.info("{} {} the friend request with {}", me, askedMe ? "rejected" : "cancelled",
                request.otherThan(me).getUsername());
        tell(request.otherThan(me).getUsername(), FriendEvent.of(FriendEventType.REMOVED, me));
    }

    /** Ends a friendship with this player (REMOVED). The row is kept. */
    @Transactional
    public void remove(String me, String username) {
        Friendship friendship = friendshipRepository.findBetween(me, username)
                .filter(link -> link.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException(ExceptionConstants.FRIEND_NOT_A_FRIEND));
        friendship.end(FriendshipStatus.REMOVED);
        friendshipRepository.save(friendship);
        log.info("{} and {} are no longer friends", me, username);
        tell(username, FriendEvent.of(FriendEventType.REMOVED, me));
    }

    @Transactional(readOnly = true)
    public boolean areFriends(String a, String b) {
        return friendshipRepository.existsBetween(a, b, FriendshipStatus.ACCEPTED);
    }

    /**
     * Players whose name starts with what was typed, to ask: never this
     * player, nor a friend already — they are in the list. One with a request
     * waiting either way is found, and says so, so a name typed is never
     * answered with "nobody" when it is somebody asked or asking. Nothing for
     * fewer than {@link #SEARCH_MIN_LENGTH} letters.
     *
     * <p>All of them, when they are no more than {@link #SEARCH_LIMIT}. When
     * there are more, none of them but the name typed out in full, if it is
     * there — so {@code player1} can be asked while {@code player10} and the
     * rest wait for more letters — and {@code more} says so.
     */
    @Transactional(readOnly = true)
    public FriendSearchResponse search(String me, String typed) {
        String prefix = typed == null ? "" : typed.trim();
        if (prefix.length() < SEARCH_MIN_LENGTH) {
            return new FriendSearchResponse(List.of(), false);
        }
        FriendLinks links = linksOf(me);
        Map<String, FriendRelation> relations = new HashMap<>();
        links.outgoing().forEach(request -> relations.put(request.username(), FriendRelation.SENT));
        links.incoming().forEach(request -> relations.put(request.username(), FriendRelation.RECEIVED));
        // One past the limit is enough to know there are more; more for this
        // player and their friends, who are left out.
        Set<String> leftOut = new HashSet<>(links.friends());
        leftOut.add(me);
        List<FriendSearchHit> found = userAccountService.usernamesStartingWith(prefix, SEARCH_LIMIT + 1 + leftOut.size())
                .stream()
                .filter(name -> !leftOut.contains(name))
                .limit(SEARCH_LIMIT + 1L)
                .map(name -> new FriendSearchHit(name, relations.getOrDefault(name, FriendRelation.NONE)))
                .toList();
        if (found.size() <= SEARCH_LIMIT) {
            return new FriendSearchResponse(found, false);
        }
        List<FriendSearchHit> exact = found.stream()
                .filter(hit -> hit.username().equalsIgnoreCase(prefix))
                .limit(1)
                .toList();
        return new FriendSearchResponse(exact, true);
    }

    private void accept(Friendship request) {
        request.end(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(request);
        String requester = request.getRequester().getUsername();
        String addressee = request.getAddressee().getUsername();
        log.info("{} and {} are friends", requester, addressee);
        tell(requester, FriendEvent.of(FriendEventType.ACCEPTED, addressee));
    }

    /**
     * Sends it once the change is committed: the screen reads the list again
     * on hearing it, and must find the change there.
     */
    private void tell(String username, FriendEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    webSocketService.notifyFriends(username, event);
                }
            });
        } else {
            webSocketService.notifyFriends(username, event);
        }
    }
}
