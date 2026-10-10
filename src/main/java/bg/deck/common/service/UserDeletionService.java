package bg.deck.common.service;

import bg.deck.common.enums.UserDeletionStatus;
import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.User;
import bg.deck.common.model.UserDeletion;
import bg.deck.common.repository.UserDeletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The account-deletion links: the only place {@link UserDeletionRepository} is
 * spoken to.
 */
@RequiredArgsConstructor
@Service
public class UserDeletionService {

    private final UserDeletionRepository userDeletionRepository;

    /** The deletion still open under this token, if there is one. */
    public Optional<UserDeletion> findPending(UUID token) {
        return userDeletionRepository.findByUserDeletionTokenAndStatus(token, UserDeletionStatus.PENDING);
    }

    /** A new link for this user, and the end of any they already had. */
    public UserDeletion issueFor(User user) {
        expirePendingFor(user);
        return userDeletionRepository.save(new UserDeletion(user));
    }

    public UserDeletion save(UserDeletion userDeletion) {
        return userDeletionRepository.save(userDeletion);
    }

    /** Retires everything still open for this user. */
    public void expirePendingFor(User user) {
        List<UserDeletion> pending =
                userDeletionRepository.findAllByUserAndStatus(user, UserDeletionStatus.PENDING);
        pending.forEach(userDeletion -> userDeletion.setStatus(UserDeletionStatus.EXPIRED));
        userDeletionRepository.saveAll(pending);
    }

    /** Retires every open link made before {@code cutoff}, and says how many. */
    public int expireOlderThan(Instant cutoff) {
        return userDeletionRepository.expireOlderThan(
                cutoff, UserDeletionStatus.PENDING, UserDeletionStatus.EXPIRED);
    }

    /**
     * Hands this user's links to the tombstone left behind by a deleted
     * account, so the rows survive without naming anyone.
     */
    public void reassignToDeletedUser(User user, DeletedUser deletedUser) {
        List<UserDeletion> userDeletions = userDeletionRepository.findAllByUser(user);
        userDeletions.forEach(userDeletion -> {
            userDeletion.setDeletedUser(deletedUser);
            userDeletion.setUser(null);
        });
        userDeletionRepository.saveAll(userDeletions);
    }
}
