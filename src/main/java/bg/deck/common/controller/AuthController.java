package bg.deck.common.controller;

import bg.deck.common.model.request.ChangeForgottenPasswordRequest;
import bg.deck.common.model.request.ForgotPasswordEmailRequest;
import bg.deck.common.model.request.LoginRequest;
import bg.deck.common.model.request.RefreshRequest;
import bg.deck.common.model.request.RegisterRequest;
import bg.deck.common.model.response.AuthResponse;
import bg.deck.common.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

import static bg.deck.common.constant.Constants.DECK_BG_INVALID_LINK;
import static bg.deck.common.constant.Constants.DECK_BG_SUCCESS_CONFIRMATION;
import static bg.deck.common.constant.Constants.TOKEN;

@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        return ResponseEntity.ok(authService.login(loginRequest, request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.ok(authService.register(registerRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@RequestParam(TOKEN) UUID confirmationToken) {

        boolean confirmed = authService.confirmEmail(confirmationToken);

        String redirectUrl = confirmed ? DECK_BG_SUCCESS_CONFIRMATION : DECK_BG_INVALID_LINK;

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    @GetMapping("/forgot-password/verify")
    public ResponseEntity<Void> verifyForgotPasswordToken(@RequestParam(TOKEN) UUID forgotPasswordToken) {
        authService.verifyForgotPasswordToken(forgotPasswordToken);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordEmailRequest forgotPasswordEmailRequest) {
        authService.forgotPassword(forgotPasswordEmailRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changeForgottenPassword(@Valid @RequestBody ChangeForgottenPasswordRequest changeForgottenPasswordRequest) {
        authService.changeForgottenPassword(changeForgottenPasswordRequest);
        return ResponseEntity.ok().build();
    }
}
