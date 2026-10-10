package bg.deck.common;

import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import bg.deck.common.exception.ServiceNotAvailableException;
import bg.deck.common.model.AvailableService;
import bg.deck.common.model.User;
import bg.deck.common.model.dto.ServiceAvailability;
import bg.deck.common.model.dto.CachedServices;
import bg.deck.common.service.AvailabilityService;
import bg.deck.common.service.CacheService;
import bg.deck.common.service.UserAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who is offered which game.
 *
 * <p>Eight cases decide it — a game on or off, wanting either scope, asked
 * about by an account holding either — so all eight are here rather than the
 * two or three that would feel like enough.
 */
@DisplayName("What a player is offered")
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    // The catalogue and how long it is held for belong to
    // CacheService and the cache around it; what is under test here
    // is only who gets offered what.
    @Mock private CacheService cacheService;
    @Mock private UserAccountService userAccountService;
    @InjectMocks private AvailabilityService availabilityService;

    private void offering(AvailableService... services) {
        Map<String, ServiceAvailability> byCode = Stream.of(services)
                .map(service -> new ServiceAvailability(
                        service.getCode(), service.getState(), service.getRequiredScope()))
                .collect(Collectors.toMap(ServiceAvailability::code, Function.identity()));
        when(cacheService.availableServices()).thenReturn(new CachedServices(byCode));
    }

    private static AvailableService service(String code, ServiceState state, Scope scope) {
        return new AvailableService(code, state, scope);
    }

    private void accountWith(String username, Scope scope) {
        User user = new User();
        user.setUsername(username);
        user.setScope(scope);
        when(userAccountService.findByUsername(username)).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("the eight cases")
    class Matrix {

        @ParameterizedTest(name = "{0} game wanting {1}, asked by a {2} account: {3}")
        @CsvSource({
                "ON,  PUBLIC, PUBLIC, true",
                "ON,  PUBLIC, BETA,   true",
                "ON,  BETA,   PUBLIC, false",
                "ON,  BETA,   BETA,   true",
                "OFF, PUBLIC, PUBLIC, false",
                "OFF, PUBLIC, BETA,   false",
                "OFF, BETA,   PUBLIC, false",
                "OFF, BETA,   BETA,   false",
        })
        void everyCombination(ServiceState state, Scope required, Scope held, boolean offered) {
            offering(service("TABLA", state, required));
            accountWith("petko91", held);

            assertEquals(offered, availabilityService.isAvailable("TABLA", "petko91"));
        }
    }

    @Nested
    @DisplayName("asked where a game begins")
    class Requiring {

        @Test
        @DisplayName("a game on offer lets the search carry on")
        void onOfferPassesThrough() {
            offering(service("TABLA", ServiceState.ON, Scope.PUBLIC));

            availabilityService.requireAvailable("TABLA", "petko91");
        }

        @Test
        @DisplayName("one that is not stops it")
        void notOnOfferThrows() {
            offering(service("TABLA", ServiceState.ON, Scope.BETA));
            accountWith("petko91", Scope.PUBLIC);

            assertThrows(ServiceNotAvailableException.class,
                    () -> availabilityService.requireAvailable("TABLA", "petko91"));
        }
    }

    @Nested
    @DisplayName("the list")
    class Listing {

        @Test
        @DisplayName("holds what the account reaches, alphabetically")
        void whatTheAccountReaches() {
            offering(
                    service("TABLA", ServiceState.ON, Scope.PUBLIC),
                    service("SANTASE", ServiceState.ON, Scope.PUBLIC),
                    service("BELOT", ServiceState.ON, Scope.BETA));
            accountWith("petko91", Scope.PUBLIC);

            assertEquals(List.of("SANTASE", "TABLA"), availabilityService.availableTo("petko91"));
        }

        @Test
        @DisplayName("and a beta account reaches the ordinary games too")
        void betaKeepsTheOrdinaryGames() {
            offering(
                    service("TABLA", ServiceState.ON, Scope.PUBLIC),
                    service("SANTASE", ServiceState.ON, Scope.PUBLIC),
                    service("BELOT", ServiceState.ON, Scope.BETA));
            accountWith("ninja2011", Scope.BETA);

            assertEquals(List.of("BELOT", "SANTASE", "TABLA"), availabilityService.availableTo("ninja2011"));
        }

        @Test
        @DisplayName("a game switched off is simply absent")
        void switchedOffIsAbsent() {
            offering(
                    service("TABLA", ServiceState.OFF, Scope.PUBLIC),
                    service("SANTASE", ServiceState.ON, Scope.PUBLIC));
            accountWith("petko91", Scope.PUBLIC);

            assertEquals(List.of("SANTASE"), availabilityService.availableTo("petko91"));
        }
    }

    @Nested
    @DisplayName("reading the account")
    class ReadingTheAccount {

        @Test
        @DisplayName("is skipped when nothing on offer asks for a scope")
        void notReadForOrdinaryGames() {
            offering(
                    service("TABLA", ServiceState.ON, Scope.PUBLIC),
                    service("SANTASE", ServiceState.ON, Scope.PUBLIC));

            availabilityService.isAvailable("TABLA", "petko91");
            availabilityService.availableTo("petko91");

            verify(userAccountService, never()).findByUsername("petko91");
        }

        @Test
        @DisplayName("and an account nobody can find is treated as ordinary")
        void anUnknownAccountIsOrdinary() {
            offering(
                    service("TABLA", ServiceState.ON, Scope.PUBLIC),
                    service("BELOT", ServiceState.ON, Scope.BETA));
            when(userAccountService.findByUsername("ghost")).thenReturn(Optional.empty());

            assertTrue(availabilityService.isAvailable("TABLA", "ghost"));
            assertFalse(availabilityService.isAvailable("BELOT", "ghost"));
        }
    }

    @Nested
    @DisplayName("a code with no row")
    class Unknown {

        @Test
        @DisplayName("is not offered, however it is spelt")
        void notOffered() {
            offering(service("TABLA", ServiceState.ON, Scope.PUBLIC));
            accountWith("petko91", Scope.BETA);

            assertFalse(availabilityService.isAvailable("BELOT", "petko91"),
                    "a game exists when the table says so, not when the code appears in the source");
            assertFalse(availabilityService.isAvailable("tabla", "petko91"), "codes are exact");
        }
    }

}
