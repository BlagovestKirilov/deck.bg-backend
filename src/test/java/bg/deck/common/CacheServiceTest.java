package bg.deck.common;

import bg.deck.common.config.CacheConfig;
import bg.deck.common.constant.Constants;
import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import bg.deck.common.model.AvailableService;
import bg.deck.common.repository.AvailableServiceRepository;
import bg.deck.common.service.CacheService;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The catalogue's cache, against the real one.
 *
 * <p>Worth booting a context for: {@code @Cacheable} is applied by a proxy, and
 * the whole arrangement — the annotation, the manager, the class sitting apart
 * from its caller so the call is not a self-invocation — either works together
 * or does nothing at all, silently. A mock cannot tell the difference.
 *
 * <p>The whole application boots, so it gets a database of its own: left to
 * {@code application.yml} it would reach for whatever {@code DB_URL_SANTASE}
 * names on the machine running the build, and Liquibase would migrate it.
 */
@DisplayName("The games on offer, cached")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:cacheservice;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class CacheServiceTest {

    @MockitoBean private AvailableServiceRepository availableServiceRepository;

    @Autowired private CacheService cacheService;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void startCold() {
        // Through the manager, not the service: nothing clears this cache by
        // hand in the running application, and a test is not a reason to add
        // a method that lets it.
        cacheManager.getCache(Constants.SERVICES_CACHE).clear();
        when(availableServiceRepository.findAll())
                .thenReturn(List.of(new AvailableService("TABLA", ServiceState.ON, Scope.PUBLIC)));
    }

    @Test
    @DisplayName("the table is read once, however often it is asked for")
    void readOnce() {
        cacheService.availableServices();
        cacheService.availableServices();
        cacheService.availableServices();

        verify(availableServiceRepository).findAll();
    }

    @Test
    @DisplayName("and read again once the entry is gone")
    void readAgainAfterTheEntryGoes() {
        cacheService.availableServices();

        // What the expiry below will do on its own, half a minute later.
        cacheManager.getCache(Constants.SERVICES_CACHE).clear();
        cacheService.availableServices();

        verify(availableServiceRepository, times(2)).findAll();
    }

    @Test
    @DisplayName("an entry lets go of itself, so an UPDATE needs no restart")
    void entriesExpireOnTheirOwn() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(Constants.SERVICES_CACHE);
        Duration expiry = cache.getNativeCache().policy().expireAfterWrite()
                .map(Policy.FixedExpiration::getExpiresAfter)
                .orElseThrow(() -> new AssertionError(
                        "without an expiry a game switched off comes back only on a restart"));

        assertEquals(CacheConfig.SERVICES_TTL, expiry);
        assertTrue(expiry.toSeconds() <= 60,
                "the promise made to whoever switches a game off during an incident");
    }
}
