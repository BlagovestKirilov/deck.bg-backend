package bg.deck.santase.service;

import bg.deck.common.exception.NoActiveGameFoundException;
import bg.deck.common.model.response.SearchGameResponse;
import bg.deck.common.service.WebSocketService;
import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseSeat;
import bg.deck.santase.repository.SantaseGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Santase's games: finding one, saving one, and who won it — none of the
 * rules, which are SantaseDealService's.
 *
 * <p>The only class that speaks to {@link SantaseGameRepository}.
 */
@RequiredArgsConstructor
@Service
public class SantaseTableService {

    private final SantaseGameRepository santaseGameRepository;
    private final WebSocketService webSocketService;

    /** This player's game in progress. */
    public SantaseGame findGameByUsername(String username) {
        return santaseGameRepository.findActiveGamesByUsername(username)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoActiveGameFoundException(username));
    }

    /** The game with this id, if it is still there. */
    public Optional<SantaseGame> findGameById(UUID gameId) {
        return santaseGameRepository.findById(gameId);
    }

    /** Every game still being played. */
    public List<SantaseGame> findAllActiveGames() {
        return santaseGameRepository.findAllActive();
    }

    public SantaseGame saveGame(SantaseGame game) {
        return santaseGameRepository.save(game);
    }

    /** The id of the game this player is in, if any. */
    public Optional<UUID> activeGameId(String username) {
        return santaseGameRepository.findActiveGameIdsByUsername(username).stream().findFirst();
    }

    /**
     * True when the player is free to start a game. A player already in one is
     * pointed back at it on their search topic — which is how a reopened tab
     * finds its way back — and is not free.
     */
    public boolean checkIfUserExistsAndIsAvailable(String username) {
        Optional<UUID> gameId = activeGameId(username);
        gameId.ifPresent(id -> webSocketService.notifyGameSearch(username, SantaseService.SANTASE,
                SearchGameResponse.started(id)));
        return gameId.isEmpty();
    }

    /** Marks the game won. Writing the result into the players' records is the caller's. */
    public void setGameWinner(SantaseGame game, SantaseSeat winner, boolean opponentSurrendered) {
        game.setWinner(winner, opponentSurrendered);
    }
}
