package bg.deck.common.service;

import bg.deck.common.constant.LogConstants;
import bg.deck.common.enums.Role;
import bg.deck.common.enums.Scope;
import bg.deck.common.model.request.AdminPlayerFilter;
import bg.deck.common.exception.NotFoundException;
import bg.deck.common.exception.UserNotFoundException;
import bg.deck.common.model.User;
import bg.deck.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The accounts themselves: the only place {@link UserRepository} is spoken to.
 *
 * <p>It depends on nothing else. That is deliberate — every other service needs
 * an account from time to time, and an owner with dependencies of its own would
 * have them all pointing back at each other.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class UserAccountService {

    private final UserRepository userRepository;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * The account, or {@link UserNotFoundException}. For the callers that have
     * a name from an authenticated session: if that name has no account, the
     * request cannot go on, and every one of them was writing the same three
     * lines to say so.
     */
    public User requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn(LogConstants.USER_NOT_FOUND, username);
                    return new UserNotFoundException(username);
                });
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public void delete(User user) {
        userRepository.delete(user);
    }

    /**
     * Whether this account is an admin, as the database has it now — not as a
     * token minted earlier says. Asked on every /admin request, so a role set
     * or taken away by hand counts from the next request on.
     */
    public boolean isAdmin(String username) {
        return username != null && userRepository.existsByUsernameAndRole(username, Role.ROLE_ADMIN);
    }

    /**
     * One page of the accounts the filter lets through, in the page's order.
     *
     * <p>The name matches anywhere in the username, in any case, and
     * {@code %} or {@code _} typed into it mean themselves. A part of the
     * filter left empty lets every account through on that part.
     */
    @Transactional(readOnly = true)
    public Page<User> find(AdminPlayerFilter filter, Pageable page) {
        return userRepository.findAll(matching(filter), page);
    }

    private static Specification<User> matching(AdminPlayerFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.username() != null && !filter.username().isBlank()) {
                String escaped = filter.username().strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                where.add(cb.like(cb.lower(root.get("username")), "%" + escaped + "%", '\\'));
            }
            if (filter.role() != null) {
                where.add(cb.equal(root.get("role"), filter.role().role()));
            }
            if (filter.scope() != null) {
                where.add(cb.equal(root.get("scope"), filter.scope()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /**
     * Notes that this account is here now — on a login, and on every token
     * refresh, which is how a player who stays signed in keeps being seen.
     * One column, by UPDATE: a whole-row save from here could carry a stale
     * scope back over an admin's change made a moment before.
     */
    @Transactional
    public void markSeen(String username) {
        userRepository.markSeen(username, Instant.now());
    }

    /** Sets how far into the site this account can see. */
    @Transactional
    public User setScope(String username, Scope scope) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("No player " + username));
        user.setScope(scope);
        return userRepository.save(user);
    }
}
