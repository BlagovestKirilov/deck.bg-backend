package bg.deck.santase.repository;

import bg.deck.santase.model.SantaseSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Santase seats. Owned by SantaseSeatService, and spoken to by nothing else. */
public interface SantaseSeatRepository extends JpaRepository<SantaseSeat, UUID> {

    /** Renames every seat a name sat in: how a deleted account leaves its games. */
    @Modifying
    @Query("UPDATE SantaseSeat seat SET seat.username = :tombstone WHERE seat.username = :username")
    int rename(@Param("username") String username, @Param("tombstone") String tombstone);
}
