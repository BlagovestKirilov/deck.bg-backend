package bg.deck.common.config;

import bg.deck.common.constant.Constants;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The caches, and how long each one is allowed to be wrong for.
 *
 * <p>There is one. The catalogue of games on offer is read on nearly every
 * request and written by hand a few times a year, so reading the table each
 * time would be a query per request for an answer that almost never changes.
 *
 * <p>The expiry is the point, not the speed. It is the promise made to whoever
 * switches a game off during an incident: an {@code UPDATE} against
 * {@code available_service} takes effect within {@link #SERVICES_TTL}, with no
 * deploy, no restart and nothing to remember to do afterwards. Caffeine is here
 * for that expiry — the simple cache manager Boot would otherwise give us holds
 * an entry for ever, which would turn that promise into a restart.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** How long the catalogue may be out of date. */
    public static final Duration SERVICES_TTL = Duration.ofSeconds(30);

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(Constants.SERVICES_CACHE);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(SERVICES_TTL));

        // Every cache is declared here with a lifetime of its own. A name that
        // turns up in an @Cacheable and nowhere here is a mistake worth hearing
        // about at the first call rather than discovering as a cache that never
        // expires.
        manager.setAllowNullValues(false);
        return manager;
    }
}
