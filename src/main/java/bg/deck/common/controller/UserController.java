package bg.deck.common.controller;

import bg.deck.common.model.request.ChangePasswordRequest;
import bg.deck.common.model.request.ConfirmDeletionRequest;
import bg.deck.common.model.request.UserDeletionRequest;
import bg.deck.common.model.response.ProfileResponse;
import bg.deck.common.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/user")
@RestController
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getProfile());
    }

    @PostMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail() {
        return userService.confirmEmail() ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest) {
        userService.changePassword(changePasswordRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete-user")
    public ResponseEntity<Void> sendUserDeletionEmail(@Valid @RequestBody UserDeletionRequest userDeletionRequest) {
        userService.sendUserDeletionEmail(userDeletionRequest);
        return ResponseEntity.ok().build();
    }

    /**
     * Confirms an account deletion. Unauthenticated — the token from the email
     * is the authorization.
     *
     * <p>A POST, and the token travels in the body. As a GET this deleted the
     * account for anything that merely fetched the URL, which is what mail
     * scanners and link prefetchers do to every link in a message. The page on
     * the site now asks the person first and reports the outcome itself, so
     * there is no redirect to hand back.
     */
    @PostMapping("/confirm-deletion")
    public ResponseEntity<Void> confirmDeletion(@Valid @RequestBody ConfirmDeletionRequest confirmDeletionRequest) {
        return userService.confirmDeletion(confirmDeletionRequest.token())
                ? ResponseEntity.ok().build()
                : ResponseEntity.badRequest().build();
    }
}
