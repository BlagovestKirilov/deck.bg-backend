package bg.deck.common.service;

import bg.deck.common.enums.EmailConfirmationStatus;
import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.User;
import bg.deck.common.repository.EmailConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The address-confirmation links: the only place
 * {@link EmailConfirmationRepository} is spoken to.
 */
@RequiredArgsConstructor
@Service
public class EmailConfirmationService {

    private final EmailConfirmationRepository emailConfirmationRepository;

    /** The confirmation still open under this token, if there is one. */
    public Optional<EmailConfirmation> findPending(UUID token) {
        return emailConfirmationRepository.findByConfirmationTokenAndStatus(
                token, EmailConfirmationStatus.PENDING);
    }

    /**
     * A new link for this user, and the end of any they already had — someone
     * who asks for the email again is told the newest one is what works.
     */
    public EmailConfirmation issueFor(User user) {
        expirePendingFor(user);
        return emailConfirmationRepository.save(new EmailConfirmation(user));
    }

    public EmailConfirmation save(EmailConfirmation emailConfirmation) {
        return emailConfirmationRepository.save(emailConfirmation);
    }

    /** Retires everything still open for this user. */
    public void expirePendingFor(User user) {
        List<EmailConfirmation> pending =
                emailConfirmationRepository.findAllByUserAndStatus(user, EmailConfirmationStatus.PENDING);
        pending.forEach(emailConfirmation -> emailConfirmation.setStatus(EmailConfirmationStatus.EXPIRED));
        emailConfirmationRepository.saveAll(pending);
    }

    /** Retires every open link made before {@code cutoff}, and says how many. */
    public int expireOlderThan(Instant cutoff) {
        return emailConfirmationRepository.expireOlderThan(
                cutoff, EmailConfirmationStatus.PENDING, EmailConfirmationStatus.EXPIRED);
    }

    /**
     * Hands this user's links to the tombstone left behind by a deleted
     * account, so the rows survive without naming anyone.
     */
    public void reassignToDeletedUser(User user, DeletedUser deletedUser) {
        List<EmailConfirmation> emailConfirmations = emailConfirmationRepository.findAllByUser(user);
        emailConfirmations.forEach(emailConfirmation -> {
            emailConfirmation.setDeletedUser(deletedUser);
            emailConfirmation.setUser(null);
        });
        emailConfirmationRepository.saveAll(emailConfirmations);
    }
}
