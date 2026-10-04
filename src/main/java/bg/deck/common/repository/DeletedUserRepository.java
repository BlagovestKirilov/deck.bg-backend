package bg.deck.common.repository;

import bg.deck.common.model.DeletedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletedUserRepository extends JpaRepository<DeletedUser, Integer> {
}
