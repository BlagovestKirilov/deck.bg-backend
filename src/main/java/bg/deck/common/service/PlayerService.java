package bg.deck.common.service;

import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.Player;
import bg.deck.common.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The seats at a table: the only place {@link PlayerRepository} is spoken to.
 */
@RequiredArgsConstructor
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public Player save(Player player) {
        return playerRepository.save(player);
    }

    /**
     * Hands every seat this user ever took to the tombstone left behind by a
     * deleted account. A user owns one row per game played, so all of them go,
     * not just the first — a finished game still has to name who sat in it.
     */
    public void reassignToDeletedUser(String username, DeletedUser deletedUser) {
        List<Player> seats = playerRepository.findAllByUserUsername(username);
        seats.forEach(player -> {
            player.setDeletedUser(deletedUser);
            player.setUser(null);
        });
        playerRepository.saveAll(seats);
    }
}
