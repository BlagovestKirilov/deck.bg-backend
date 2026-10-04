package bg.deck.belot.controller;

import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotCutRequest;
import bg.deck.belot.model.request.BelotPlayRequest;
import bg.deck.belot.model.response.BelotProfileResponse;
import bg.deck.belot.service.BelotPlayerService;
import bg.deck.belot.service.BelotStatsService;
import bg.deck.belot.service.BelotService;
import bg.deck.util.AuthenticatedUser;
import bg.deck.util.RankLadder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Belot, over HTTP for the asking and STOMP for the answering.
 *
 * <p>Every endpoint answers 202 with an empty body, exactly as сантасе and
 * табла do: what the player is waiting for is their own view of the table, and
 * that arrives on {@code /topic/belot/{gameId}/{username}} — where the other
 * three players' views cannot follow it.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/belot")
public class BelotController {

    private final BelotService belotService;
    private final BelotPlayerService belotPlayerService;
    private final BelotStatsService belotStatsService;

    /** Sit down: at the table this player already has, or at one short of four. */
    @PostMapping("/search")
    public ResponseEntity<Void> search() {
        belotService.search(AuthenticatedUser.username());
        return ResponseEntity.accepted().build();
    }

    /** Send me my view again — a reload, or a socket that dropped. */
    @GetMapping("/state")
    public ResponseEntity<Void> state() {
        belotService.sendState(AuthenticatedUser.username());
        return ResponseEntity.accepted().build();
    }

    /** Pass, bid, contra or recontra, when the bidding reaches this player. */
    @PostMapping("/bid")
    public ResponseEntity<Void> bid(@Valid @RequestBody BelotBidRequest request) {
        belotService.bid(AuthenticatedUser.username(), request);
        return ResponseEntity.accepted().build();
    }

    /** Cut the deck, when this player is the one on the dealer's left. */
    @PostMapping("/cut")
    public ResponseEntity<Void> cut(@Valid @RequestBody BelotCutRequest request) {
        belotService.cut(AuthenticatedUser.username(), request);
        return ResponseEntity.accepted().build();
    }

    /** Put a card on the table, when it is this player’s turn. */
    @PostMapping("/play")
    public ResponseEntity<Void> play(@Valid @RequestBody BelotPlayRequest request) {
        belotService.play(AuthenticatedUser.username(), request);
        return ResponseEntity.accepted().build();
    }

    /**
     * This player's clock has reached nought — act for whoever is out of time.
     *
     * <p>Only a nudge: the server checks its own deadline, and does nothing
     * if the turn has not run out.
     */
    @PostMapping("/timeout")
    public ResponseEntity<Void> timeout() {
        belotService.timeUp(AuthenticatedUser.username());
        return ResponseEntity.accepted().build();
    }

    /**
     * Give up the game. It is given up for the pair, not for one seat.
     *
     * <p>No body: the seat comes from the token, as everywhere else here.
     */
    @PostMapping("/surrender")
    public ResponseEntity<Void> surrender() {
        belotService.surrender(AuthenticatedUser.username());
        return ResponseEntity.accepted().build();
    }

    /** This player’s belot record. The profile page asks santase separately. */
    @GetMapping("/profile")
    public ResponseEntity<BelotProfileResponse> profile() {
        var stats = belotStatsService.of(AuthenticatedUser.username());
        return ResponseEntity.ok(new BelotProfileResponse(
                stats.getGames(),
                stats.getWins(),
                stats.getLosses(),
                stats.getRank().name(),
                RankLadder.placementGamesRemaining(stats.getGames())));
    }

    /**
     * Proves the seam end to end: a real session reaches belot, and belot
     * writes to its own schema and nowhere else.
     */
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        belotPlayerService.ensureKnown(AuthenticatedUser.username());
        return ResponseEntity.noContent().build();
    }
}
