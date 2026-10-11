package bg.deck.common.service;

import bg.deck.common.enums.AccountRole;
import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import bg.deck.common.exception.BadRequestException;
import bg.deck.common.exception.NotFoundException;
import bg.deck.common.model.User;
import bg.deck.common.model.dto.ServiceAvailability;
import bg.deck.common.model.request.AdminPlayerFilter;
import bg.deck.common.model.response.AdminGameDetail;
import bg.deck.common.model.response.AdminPlayerDetail;
import bg.deck.common.model.response.AdminPlayerPage;
import bg.deck.common.model.response.AdminPlayerRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * What an admin can do: switch games on and off, choose who sees them, set a
 * player's scope, and look a player up.
 *
 * <p>Who may call it is settled before the request arrives — /admin/** is
 * open only to an account the database says is an admin. Every change is
 * logged with the admin who made it, what it was and what it became.
 *
 * <p>Nothing here grants or takes away the admin role. That is done by hand in
 * the database, so no session, however it was obtained, can make an admin.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class AdminService {

    /** The most accounts one page of the list may hold, whatever the request asks. */
    static final int MAX_PAGE_SIZE = 50;

    /** The fields the list may be sorted by. Anything else is refused rather than guessed at. */
    static final Set<String> SORTABLE = Set.of("createdAt", "username", "lastLoginAt");

    /** Sorted by, it puts the accounts that have never been seen at the end, either way. */
    static final String LAST_LOGIN = "lastLoginAt";

    /** The list's order when the request names none: the newest accounts first. */
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    /** The last this many finished games are shown, per game. */
    static final int RECENT_GAMES = 10;

    private final CacheService cacheService;
    private final UserAccountService userAccountService;
    private final List<GameAdminView> games;

    public List<ServiceAvailability> services() {
        return cacheService.allServices();
    }

    public ServiceAvailability updateService(String admin, String code, ServiceState state, Scope requiredScope) {
        ServiceAvailability before = cacheService.allServices().stream()
                .filter(service -> service.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No game " + code));
        ServiceAvailability after = cacheService.updateService(code, state, requiredScope);
        log.info("Admin {} set game {}: {}/{} -> {}/{}", admin, code,
                before.state(), before.requiredScope(), after.state(), after.requiredScope());
        return after;
    }

    /** One page of the players the filter lets through. */
    public AdminPlayerPage players(AdminPlayerFilter filter, Pageable requested) {
        Page<User> found = userAccountService.find(filter, pageOf(requested));
        return new AdminPlayerPage(
                found.getContent().stream().map(AdminService::rowOf).toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }

    /**
     * The page as asked for, kept to what the list allows: a size of at most
     * {@link #MAX_PAGE_SIZE}, and a sort by the known fields only, newest first
     * when none is named. The username breaks a tie last, so two accounts made
     * in the same instant keep their places from one page to the next.
     */
    static Pageable pageOf(Pageable requested) {
        Sort requestedSort = requested.getSort().isSorted() ? requested.getSort() : DEFAULT_SORT;
        for (Sort.Order order : requestedSort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException("Players cannot be sorted by " + order.getProperty());
            }
        }
        // Postgres puts NULL first in a descending order; nobody looking for who
        // was here last wants a page of players who never came back. Spring
        // Data carries nullsLast into the query (Hibernate leaves the clause out
        // only where the database already sorts that way).
        Sort asked = Sort.by(requestedSort.stream()
                .map(order -> LAST_LOGIN.equals(order.getProperty()) ? order.nullsLast() : order)
                .toList());
        Sort sort = asked.getOrderFor("username") == null
                ? asked.and(Sort.by(Sort.Direction.ASC, "username"))
                : asked;
        int size = Math.clamp(requested.getPageSize(), 1, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(requested.getPageNumber(), 0), size, sort);
    }

    public AdminPlayerDetail player(String username) {
        User user = userAccountService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("No player " + username));
        List<AdminGameDetail> perGame = games.stream()
                .map(game -> new AdminGameDetail(
                        game.code(),
                        game.recordOf(username),
                        game.activeGameId(username).orElse(null),
                        game.recentGames(username, RECENT_GAMES)))
                .toList();
        return new AdminPlayerDetail(rowOf(user), perGame);
    }

    public AdminPlayerRow setScope(String admin, String username, Scope scope) {
        Scope before = userAccountService.findByUsername(username)
                .map(User::getScope)
                .orElseThrow(() -> new NotFoundException("No player " + username));
        User user = userAccountService.setScope(username, scope);
        log.info("Admin {} set scope of {}: {} -> {}", admin, username, before, scope);
        return rowOf(user);
    }

    private static AdminPlayerRow rowOf(User user) {
        return new AdminPlayerRow(
                user.getUsername(),
                user.getEmail(),
                AccountRole.of(user.getRole()),
                user.getScope(),
                Boolean.TRUE.equals(user.getIsEmailConfirmed()),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
