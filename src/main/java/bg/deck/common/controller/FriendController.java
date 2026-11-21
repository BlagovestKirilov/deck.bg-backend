package bg.deck.common.controller;

import bg.deck.common.model.request.AddFriendRequest;
import bg.deck.common.model.request.ChallengeRequest;
import bg.deck.common.model.response.ChallengeAcceptedResponse;
import bg.deck.common.model.response.FriendSearchResponse;
import bg.deck.common.model.response.FriendsResponse;
import bg.deck.common.service.ChallengeService;
import bg.deck.common.service.FriendsService;
import bg.deck.common.service.FriendshipService;
import bg.deck.common.util.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Friends: the list, requests, and challenges to a game. Every change answers
 * with the page as it now stands, so the screen needs no second call.
 */
@RequiredArgsConstructor
@RequestMapping("/friends")
@RestController
public class FriendController {

    private final FriendsService friendsService;
    private final FriendshipService friendshipService;
    private final ChallengeService challengeService;

    @GetMapping
    public ResponseEntity<FriendsResponse> page() {
        return ResponseEntity.ok(friendsService.pageOf(AuthenticatedUser.username()));
    }

    @GetMapping("/search")
    public ResponseEntity<FriendSearchResponse> search(@RequestParam("q") String typed) {
        return ResponseEntity.ok(friendshipService.search(AuthenticatedUser.username(), typed));
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendsResponse> request(@Valid @RequestBody AddFriendRequest request) {
        String me = AuthenticatedUser.username();
        friendshipService.request(me, request.username());
        return ResponseEntity.ok(friendsService.pageOf(me));
    }

    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<FriendsResponse> accept(@PathVariable UUID id) {
        String me = AuthenticatedUser.username();
        friendshipService.accept(me, id);
        return ResponseEntity.ok(friendsService.pageOf(me));
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<FriendsResponse> decline(@PathVariable UUID id) {
        String me = AuthenticatedUser.username();
        friendshipService.decline(me, id);
        return ResponseEntity.ok(friendsService.pageOf(me));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<FriendsResponse> remove(@PathVariable String username) {
        String me = AuthenticatedUser.username();
        friendshipService.remove(me, username);
        return ResponseEntity.ok(friendsService.pageOf(me));
    }

    @PostMapping("/challenges")
    public ResponseEntity<FriendsResponse> challenge(@Valid @RequestBody ChallengeRequest request) {
        String me = AuthenticatedUser.username();
        challengeService.challenge(me, request.username(), request.game());
        return ResponseEntity.ok(friendsService.pageOf(me));
    }

    @PostMapping("/challenges/{id}/accept")
    public ResponseEntity<ChallengeAcceptedResponse> acceptChallenge(@PathVariable UUID id) {
        return ResponseEntity.ok(challengeService.accept(AuthenticatedUser.username(), id));
    }

    @PostMapping("/challenges/{id}/decline")
    public ResponseEntity<FriendsResponse> declineChallenge(@PathVariable UUID id) {
        String me = AuthenticatedUser.username();
        challengeService.decline(me, id);
        return ResponseEntity.ok(friendsService.pageOf(me));
    }

    @DeleteMapping("/challenges/{id}")
    public ResponseEntity<FriendsResponse> cancelChallenge(@PathVariable UUID id) {
        String me = AuthenticatedUser.username();
        challengeService.cancel(me, id);
        return ResponseEntity.ok(friendsService.pageOf(me));
    }
}
