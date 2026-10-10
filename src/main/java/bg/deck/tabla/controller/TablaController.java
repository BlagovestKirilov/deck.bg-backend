package bg.deck.tabla.controller;

import bg.deck.tabla.model.request.MoveRequest;
import bg.deck.tabla.service.TablaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import bg.deck.santase.controller.SantaseController;

/**
 * Обикновена табла.
 *
 * <p>Mirrors {@link SantaseController}: every endpoint returns 202 with an empty
 * body and all real output is pushed over STOMP, so both games behave the same
 * way from the client's point of view.
 */
@RequiredArgsConstructor
@RequestMapping("/tabla")
@RestController
public class TablaController {

    private final TablaService tablaService;

    @PostMapping("/search")
    public ResponseEntity<Void> search() {
        tablaService.searchGame();
        return ResponseEntity.accepted().build();
    }

    /**
     * The game this player is already in, if any: 202 and its id on the search
     * topic, or 204 when there is none and the screen offers a search.
     */
    @GetMapping("/active")
    public ResponseEntity<Void> active() {
        return tablaService.resumeActiveGame()
                ? ResponseEntity.accepted().build()
                : ResponseEntity.noContent().build();
    }

    @GetMapping("/state")
    public ResponseEntity<Void> state() {
        tablaService.getState();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/opening-throw")
    public ResponseEntity<Void> openingThrow() {
        tablaService.openingThrow();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/roll")
    public ResponseEntity<Void> roll() {
        tablaService.roll();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/move")
    public ResponseEntity<Void> move(@Valid @RequestBody MoveRequest request) {
        tablaService.move(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/undo")
    public ResponseEntity<Void> undo() {
        tablaService.undo();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm() {
        tablaService.confirm();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/surrender")
    public ResponseEntity<Void> surrender() {
        tablaService.surrender();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/inactivity")
    public ResponseEntity<Void> inactivity() {
        tablaService.reportInactivity();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/extend-time")
    public ResponseEntity<Void> extendTime() {
        tablaService.extendTime();
        return ResponseEntity.accepted().build();
    }
}
