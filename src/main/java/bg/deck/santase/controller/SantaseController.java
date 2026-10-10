package bg.deck.santase.controller;

import bg.deck.santase.model.request.CardRequest;
import bg.deck.santase.service.SantaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/santase")
@RestController
public class SantaseController {

    private final SantaseService santaseService;

    @PostMapping("/search")
    public ResponseEntity<Void> searchGame() {
        santaseService.searchGame();
        return ResponseEntity.accepted().build();
    }

    /**
     * The game this player is already in, if any: 202 and its id on the search
     * topic, or 204 when there is none and the screen offers a search.
     */
    @GetMapping("/active")
    public ResponseEntity<Void> active() {
        return santaseService.resumeActiveGame()
                ? ResponseEntity.accepted().build()
                : ResponseEntity.noContent().build();
    }

    @GetMapping("/state")
    public ResponseEntity<Void> state() {
        santaseService.getGameState();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/play-card")
    public ResponseEntity<Void> playCard(@Valid @RequestBody CardRequest cardRequest) {
        santaseService.playCard(cardRequest);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/announce")
    public ResponseEntity<Void> announceCombination(@Valid @RequestBody CardRequest cardRequest) {
        santaseService.announceCombination(cardRequest);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/close-deck")
    public ResponseEntity<Void> closeDeck() {
        santaseService.closeDeck();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/replace-card")
    public ResponseEntity<Void> replaceCard() {
        santaseService.replaceCard();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/finish-deal")
    public ResponseEntity<Void> finishDeal() {
        santaseService.finishDeal();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/surrender")
    public ResponseEntity<Void> surrender() {
        santaseService.surrender();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/inactivity")
    public ResponseEntity<Void> inactivity() {
        santaseService.inactivity();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/extend-time")
    public ResponseEntity<Void> extendTime() {
        santaseService.extendNextMoveTime();
        return ResponseEntity.accepted().build();
    }
}
