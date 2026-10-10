package bg.deck.common.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.exception.NoActiveGameFoundException;
import bg.deck.common.model.Game;
import bg.deck.common.model.Player;
import bg.deck.common.model.User;
import bg.deck.common.model.response.SearchGameResponse;
import bg.deck.common.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The two-player game table santase and табла share, and nothing about either
 * game's rules: finding a game, saving it, a fresh seat, the winner.
 *
 * <p>Santase's rules live in {@code SantaseDealService} and табла's in
 * {@code TablaUtilService}; each asks this for the table. The only class that
 * speaks to {@link GameRepository}.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class GameUtilService {
    private final GameRepository gameRepository;
    private final PlayerService playerService;
    private final UserAccountService userAccountService;
    private final WebSocketService webSocketService;
    private final RankingService rankingService;

    public Game findGameByUsername(String username, GameType gameType) {
        return gameRepository.findActiveGamesByUsernameAndType(username, gameType)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoActiveGameFoundException(username));
    }

    /** The game with this id, if it is still there. */
    public Optional<Game> findGameById(UUID gameId) {
        return gameRepository.findById(gameId);
    }

    /** Every game of this type still being played. */
    public List<Game> findAllActiveGames(GameType gameType) {
        return gameRepository.findAllActiveByType(gameType);
    }

    public Game saveGame(Game game) {
        return gameRepository.save(game);
    }

    /**
     * True when the user is free to start a game of this type. Scoped by type so
     * a live Santase game no longer blocks a табла search.
     */
    public boolean checkIfUserExistsAndIsAvailable(String username, GameType gameType) {
        Optional<UUID> gameId = gameRepository.findActiveGameIdByUsernameAndType(username, gameType);

        if (gameId.isPresent()) {
            webSocketService.notifyGameSearch(username, gameType, SearchGameResponse.started(gameId.get()));
            return false;
        } else {
            return true;
        }
    }

    /** Creates a fresh seat for a new game. */
    @Transactional
    public Player newPlayerFor(String username) {
        User user = userAccountService.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException(username));
        return playerService.save(Player.builder().user(user).build());
    }

    public void setGameWinner(Game game, Player winner, boolean opponentSurrendered) {
        game.setWinner(winner, opponentSurrendered);
        rankingService.updateRankingAfterGame(game);
    }
}
