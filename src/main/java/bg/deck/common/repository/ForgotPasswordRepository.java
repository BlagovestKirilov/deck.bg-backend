package bg.deck.common.repository;

import bg.deck.common.enums.ForgotPasswordStatus;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForgotPasswordRepository extends JpaRepository<ForgotPassword, UUID> {
    Optional<ForgotPassword> findByForgotPasswordTokenAndStatus(UUID token, ForgotPasswordStatus status);

    List<ForgotPassword> findAllByUserAndStatus(User user, ForgotPasswordStatus status);

    List<ForgotPassword> findAllByUser(User user);

    /**
     * Retires every ForgotPassword link still open that was made before
     * {@code cutoff}, and says how many. The scheduled job behind it only keeps the
     * table honest — a link this old is already refused when it is used.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update ForgotPassword t
               set t.status = :expired
             where t.status = :pending
               and t.createdAt < :cutoff
            """)
    int expireOlderThan(@Param("cutoff") Instant cutoff,
                        @Param("pending") ForgotPasswordStatus pending,
                        @Param("expired") ForgotPasswordStatus expired);
}
