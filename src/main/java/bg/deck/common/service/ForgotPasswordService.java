package bg.deck.common.service;

import bg.deck.common.enums.ForgotPasswordStatus;
import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.User;
import bg.deck.common.repository.ForgotPasswordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The password-reset links: the only place {@link ForgotPasswordRepository} is
 * spoken to.
 *
 * <p>The rule that only the newest link works lives here, in {@link
 * #issueFor(User)}, rather than in each caller that happens to need a link.
 */
@RequiredArgsConstructor
@Service
public class ForgotPasswordService {

    private final ForgotPasswordRepository forgotPasswordRepository;

    /** The reset still open under this token, if there is one. */
    public Optional<ForgotPassword> findPending(UUID token) {
        return forgotPasswordRepository.findByForgotPasswordTokenAndStatus(token, ForgotPasswordStatus.PENDING);
    }

    /**
     * A new link for this user, and the end of any they already had.
     *
     * <p>Both halves belong together: a second live link is a second way into
     * the account, and the email says the newest one is the one that works.
     */
    public ForgotPassword issueFor(User user) {
        expirePendingFor(user);
        return forgotPasswordRepository.save(new ForgotPassword(user));
    }

    public ForgotPassword save(ForgotPassword forgotPassword) {
        return forgotPasswordRepository.save(forgotPassword);
    }

    /** Retires everything still open for this user. */
    public void expirePendingFor(User user) {
        List<ForgotPassword> pending =
                forgotPasswordRepository.findAllByUserAndStatus(user, ForgotPasswordStatus.PENDING);
        pending.forEach(forgotPassword -> forgotPassword.setStatus(ForgotPasswordStatus.EXPIRED));
        forgotPasswordRepository.saveAll(pending);
    }

    /** Retires every open link made before {@code cutoff}, and says how many. */
    public int expireOlderThan(Instant cutoff) {
        return forgotPasswordRepository.expireOlderThan(
                cutoff, ForgotPasswordStatus.PENDING, ForgotPasswordStatus.EXPIRED);
    }

    /**
     * Hands this user's links to the tombstone left behind by a deleted
     * account, so the rows survive without naming anyone.
     */
    public void reassignToDeletedUser(User user, DeletedUser deletedUser) {
        List<ForgotPassword> forgotPasswords = forgotPasswordRepository.findAllByUser(user);
        forgotPasswords.forEach(forgotPassword -> {
            forgotPassword.setDeletedUser(deletedUser);
            forgotPassword.setUser(null);
        });
        forgotPasswordRepository.saveAll(forgotPasswords);
    }
}
