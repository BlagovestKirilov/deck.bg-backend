package bg.deck.common.repository;

import bg.deck.common.enums.UserDeletionStatus;
import bg.deck.common.model.User;
import bg.deck.common.model.UserDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeletionRepository extends JpaRepository<UserDeletion, Integer> {
    Optional<UserDeletion> findByUserDeletionTokenAndStatus(UUID token, UserDeletionStatus status);

    List<UserDeletion> findAllByUserAndStatus(User user, UserDeletionStatus status);

    List<UserDeletion> findAllByUser(User user);

    /**
     * Retires every UserDeletion link still open that was made before
     * {@code cutoff}, and says how many. The scheduled job behind it only keeps the
     * table honest — a link this old is already refused when it is used.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update UserDeletion t
               set t.status = :expired
             where t.status = :pending
               and t.createdAt < :cutoff
            """)
    int expireOlderThan(@Param("cutoff") Instant cutoff,
                        @Param("pending") UserDeletionStatus pending,
                        @Param("expired") UserDeletionStatus expired);
}
