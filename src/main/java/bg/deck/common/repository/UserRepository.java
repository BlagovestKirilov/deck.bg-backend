package bg.deck.common.repository;

import bg.deck.common.enums.Role;
import bg.deck.common.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    /** Every account whose name is this one in some case: usernames are unique as typed, not ignoring case. */
    List<User> findAllByUsernameIgnoreCase(String username);

    /** Names starting with what was typed, any case; the like-wildcards in it are escaped. */
    List<User> findByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(String prefix, Pageable page);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndRole(String username, Role role);

    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.username = :username")
    int markSeen(@Param("username") String username, @Param("at") Instant at);
}
