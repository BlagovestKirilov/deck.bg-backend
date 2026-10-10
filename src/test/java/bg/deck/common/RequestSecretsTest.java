package bg.deck.common;

import bg.deck.common.model.request.ChangeForgottenPasswordRequest;
import bg.deck.common.model.request.ChangePasswordRequest;
import bg.deck.common.model.request.ConfirmDeletionRequest;
import bg.deck.common.model.request.LoginRequest;
import bg.deck.common.model.request.RefreshRequest;
import bg.deck.common.model.request.RegisterRequest;
import bg.deck.common.model.request.UserDeletionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record prints every component in toString(), so the requests that carry a
 * password or a one-time token override it. One stray log line must never
 * be enough to leak either.
 */
@DisplayName("Requests carrying secrets")
class RequestSecretsTest {

    private static final String SECRET = "hunter2secret";
    private static final UUID TOKEN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("never print them")
    void secretsAreMasked() {
        List<Object> requests = List.of(
                new LoginRequest("petko91", SECRET),
                new RegisterRequest("petko91", SECRET, "petko@example.com"),
                new UserDeletionRequest(SECRET),
                new RefreshRequest(SECRET),
                new ChangePasswordRequest(SECRET, SECRET),
                new ChangeForgottenPasswordRequest(SECRET, TOKEN),
                new ConfirmDeletionRequest(TOKEN));

        for (Object request : requests) {
            String printed = request.toString();
            assertFalse(printed.contains(SECRET), "password leaked: " + printed);
            assertFalse(printed.contains(TOKEN.toString()), "token leaked: " + printed);
            assertTrue(printed.contains("***"), "masked: " + printed);
        }
    }

    @Test
    @DisplayName("still print what is not secret, for debugging")
    void theRestIsVisible() {
        String printed = new RegisterRequest("petko91", SECRET, "petko@example.com").toString();
        assertTrue(printed.contains("petko91") && printed.contains("petko@example.com"), printed);
    }
}
