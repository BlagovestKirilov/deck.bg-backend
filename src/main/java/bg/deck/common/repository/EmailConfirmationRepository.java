package bg.deck.common.repository;

import bg.deck.common.enums.EmailConfirmationStatus;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailConfirmationRepository extends JpaRepository<EmailConfirmation, UUID> {
    Optional<EmailConfirmation> findByConfirmationTokenAndStatus(UUID confirmationToken, EmailConfirmationStatus status);

    List<EmailConfirmation> findAllByUserAndStatus(User user, EmailConfirmationStatus status);

    List<EmailConfirmation> findAllByUser(User user);

    /**
     * Retires every EmailConfirmation link still open that was made before
     * {@code cutoff}, and says how many. The scheduled job behind it only keeps the
     * table honest — a link this old is already refused when it is used.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update EmailConfirmation t
               set t.status = :expired
             where t.status = :pending
               and t.createdAt < :cutoff
            """)
    int expireOlderThan(@Param("cutoff") Instant cutoff,
                        @Param("pending") EmailConfirmationStatus pending,
                        @Param("expired") EmailConfirmationStatus expired);
}
