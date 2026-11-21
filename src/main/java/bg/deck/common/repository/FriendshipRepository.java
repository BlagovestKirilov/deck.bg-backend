package bg.deck.common.repository;

import bg.deck.common.enums.FriendshipStatus;
import bg.deck.common.model.Friendship;
import bg.deck.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /** Every friendship and request this player is in, either side. */
    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee "
            + "where f.requester.username = :username or f.addressee.username = :username")
    List<Friendship> findAllOf(@Param("username") String username);

    /** The one row between two accounts, whichever of them asked. */
    @Query("select f from Friendship f where (f.requester = :a and f.addressee = :b) "
            + "or (f.requester = :b and f.addressee = :a)")
    Optional<Friendship> findBetween(@Param("a") User a, @Param("b") User b);

    /** The row between two players by name, whichever of them asked. */
    @Query("select f from Friendship f where (f.requester.username = :a and f.addressee.username = :b) "
            + "or (f.requester.username = :b and f.addressee.username = :a)")
    Optional<Friendship> findBetween(@Param("a") String a, @Param("b") String b);

    @Query("select count(f) > 0 from Friendship f where f.status = :status and "
            + "((f.requester.username = :a and f.addressee.username = :b) "
            + "or (f.requester.username = :b and f.addressee.username = :a))")
    boolean existsBetween(@Param("a") String a, @Param("b") String b, @Param("status") FriendshipStatus status);
}
