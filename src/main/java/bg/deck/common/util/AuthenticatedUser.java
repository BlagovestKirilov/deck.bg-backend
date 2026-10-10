package bg.deck.common.util;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * The name on the current request's token.
 *
 * <p>Reads the security context and nothing else — no table, no repository — so
 * a part of the service that must not touch another's data can still know who
 * is calling.
 */
@UtilityClass
public class AuthenticatedUser {

    public static String username() {
        return Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
    }
}
