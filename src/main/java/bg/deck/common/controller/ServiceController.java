package bg.deck.common.controller;

import bg.deck.common.model.response.AvailableServicesResponse;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.util.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What this player is being offered.
 *
 * <p>Answered for whoever is asking, so the client never sees a game it may not
 * have, nor learns that one exists.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/services")
public class ServiceController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<AvailableServicesResponse> available() {
        return ResponseEntity.ok(
                new AvailableServicesResponse(availabilityService.availableTo(AuthenticatedUser.username())));
    }
}
