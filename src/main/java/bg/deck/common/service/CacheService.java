package bg.deck.common.service;

import bg.deck.common.constant.Constants;
import bg.deck.common.model.AvailableService;
import bg.deck.common.model.dto.CachedServices;
import bg.deck.common.model.dto.ServiceAvailability;
import bg.deck.common.repository.AvailableServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The cached reads, and the only class that speaks to
 * {@link AvailableServiceRepository}.
 *
 * <p>Today there is one such read: the catalogue of games on offer, which
 * nearly every request needs and which changes a few times a year.
 *
 * <p>It is a class of its own rather than a method on {@link AvailabilityService}
 * because {@code @Cacheable} is applied by a proxy around the bean, and a bean
 * calling its own method never goes through that proxy. The caller has to
 * arrive from outside, so a cached read cannot sit beside the code that uses
 * it. Anything else worth caching later belongs here for the same reason.
 *
 * <p>How long an entry lives is {@link bg.deck.common.config.CacheConfig}'s business,
 * not this class's.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class CacheService {

    private final AvailableServiceRepository availableServiceRepository;

    /**
     * Every game the table knows about, by code.
     *
     * <p>Read at most once every {@code CacheConfig.SERVICES_TTL}; in between,
     * callers get the copy. Nothing clears it by hand — the entry lets go of
     * itself, which is what makes an {@code UPDATE} against the table enough on
     * its own, with no deploy and nothing to remember afterwards.
     */
    @Cacheable(Constants.SERVICES_CACHE)
    @Transactional(readOnly = true)
    public CachedServices availableServices() {
        Map<String, ServiceAvailability> byCode = availableServiceRepository.findAll().stream()
                .map(CacheService::copyOf)
                .collect(Collectors.toMap(ServiceAvailability::code, Function.identity()));

        log.debug("Read the catalogue: {}", byCode.keySet());
        return new CachedServices(byCode);
    }

    private static ServiceAvailability copyOf(AvailableService service) {
        return new ServiceAvailability(service.getCode(), service.getState(), service.getRequiredScope());
    }
}
