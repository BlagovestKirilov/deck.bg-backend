package bg.deck.common.service;

import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import bg.deck.common.exception.ServiceNotAvailableException;
import bg.deck.common.model.User;
import bg.deck.common.model.dto.CachedServices;
import bg.deck.common.model.dto.ServiceAvailability;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Which games are being offered, and to whom.
 *
 * <p>The catalogue itself is {@link CacheService}'s, cached there for
 * half a minute at a time — which is also the promise made to whoever turns a
 * game off: an UPDATE against the table takes effect within that window, with
 * no deploy and no restart.
 *
 * <p>A game that is on and public is answered without reading the account at
 * all — which is every request, most days. The scope is fetched only when a
 * game asks for more than {@link Scope#PUBLIC}, and through
 * {@link UserAccountService}, never the user table directly.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class AvailabilityService {

    private final CacheService cacheService;
    private final UserAccountService userAccountService;

    /**
     * True when this player may start a game of this kind.
     *
     * <p>A code with no row is not offered. That is deliberate: a game is
     * visible once somebody has said so in the table, not merely because the
     * code exists somewhere in the source.
     */
    public boolean isAvailable(String code, String username) {
        return cacheService.availableServices().find(code)
                .filter(service -> service.state() == ServiceState.ON)
                .map(service -> service.requiredScope() == Scope.PUBLIC
                        || scopeOf(username).covers(service.requiredScope()))
                .orElse(false);
    }

    /**
     * The same question, asked where a game begins.
     *
     * <p>The annotation on the endpoint stops a request at the door; this
     * stops the search itself, so a caller that is not an HTTP request —
     * a socket, a scheduler, whatever is written next — cannot put a
     * player into the queue for a game nobody is being offered.
     */
    public void requireAvailable(String code, String username) {
        if (!isAvailable(code, username)) {
            throw new ServiceNotAvailableException(code);
        }
    }

    /** Everything this player may see, alphabetically. */
    public List<String> availableTo(String username) {
        CachedServices services = cacheService.availableServices();

        boolean anyNeedsAScope = services.all().stream()
                .anyMatch(service -> service.state() == ServiceState.ON
                        && service.requiredScope() != Scope.PUBLIC);
        Scope scope = anyNeedsAScope ? scopeOf(username) : Scope.PUBLIC;

        return services.all().stream()
                .filter(service -> service.state() == ServiceState.ON)
                .filter(service -> scope.covers(service.requiredScope()))
                .map(ServiceAvailability::code)
                .sorted()
                .toList();
    }

    /** An account nobody can find is treated as ordinary, and sees the ordinary games. */
    private Scope scopeOf(String username) {
        return userAccountService.findByUsername(username)
                .map(User::getScope)
                .orElse(Scope.PUBLIC);
    }
}
