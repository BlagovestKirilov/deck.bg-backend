package bg.deck.common.service;

import bg.deck.common.constant.LogConstants;
import bg.deck.common.exception.UserNotFoundException;
import bg.deck.common.model.User;
import bg.deck.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

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
}
