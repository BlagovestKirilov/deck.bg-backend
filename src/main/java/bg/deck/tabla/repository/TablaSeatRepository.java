package bg.deck.tabla.repository;

import bg.deck.tabla.model.TablaSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Табла seats. Owned by TablaSeatService, and spoken to by nothing else. */
public interface TablaSeatRepository extends JpaRepository<TablaSeat, UUID> {

    /** Renames every seat a name sat in: how a deleted account leaves its games. */
    @Modifying
    @Query("UPDATE TablaSeat seat SET seat.username = :tombstone WHERE seat.username = :username")
    int rename(@Param("username") String username, @Param("tombstone") String tombstone);
}
