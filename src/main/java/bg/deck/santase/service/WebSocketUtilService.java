package bg.deck.santase.service;

import bg.deck.common.model.Game;
import bg.deck.santase.model.GameState;
import bg.deck.common.model.Player;
import bg.deck.santase.model.dto.CardDTO;
import bg.deck.santase.model.response.GameStateResponse;
import bg.deck.santase.util.CardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import bg.deck.common.service.WebSocketService;

@RequiredArgsConstructor
@Service
public class WebSocketUtilService {
    private final WebSocketService webSocketService;
    private final CardMapper cardMapper;

    public void updateGameState(
            Game game,
            String username,
            String trickWinnerUsername,
            int trickFirstPlayerScore,
            int trickSecondPlayerScore
    ) {
        GameStateResponse response = buildBaseGameStateResponse(game, username)
                .toBuilder()
                .trickWinnerUsername(trickWinnerUsername)
                .trickFirstPlayerScore(trickFirstPlayerScore)
                .trickSecondPlayerScore(trickSecondPlayerScore)
                .build();

        webSocketService.notifyGameUpdate(game.getId().toString(), username, response);
    }

    public void updateGameState(Game game) {
        List<String> players = List.of(game.getFirstPlayer().getUsername(),
                game.getSecondPlayer().getUsername());

        for (String player : players) {
            GameStateResponse response = buildBaseGameStateResponse(game, player);
            webSocketService.notifyGameUpdate(game.getId().toString(), player, response);
        }
    }

    /**
     * Both cards of a trick on the table, and whose it is. Sent before the
     * trick is taken, so both screens can ring the winning card while the
     * two are still out.
     */
    public void updateGameStateWithTrickTaker(Game game, String takenBy) {
        for (Player player : List.of(game.getFirstPlayer(), game.getSecondPlayer())) {
            GameStateResponse response = buildBaseGameStateResponse(game, player.getUsername())
                    .toBuilder()
                    .trickTakenBy(takenBy)
                    .build();
            webSocketService.notifyGameUpdate(game.getId().toString(), player.getUsername(), response);
        }
    }

    public void updateGameStateWithTrickWinner(Game game, String trickWinner) {
        List<Player> players = List.of(game.getFirstPlayer(), game.getSecondPlayer());

        for (Player player : players) {
            String username = player.getUsername();

            GameStateResponse response = buildBaseGameStateResponse(game, username)
                    .toBuilder()
                    .trickWinnerUsername(trickWinner)
                    .trickFirstPlayerScore(game.getFirstPlayer().getScore())
                    .trickSecondPlayerScore(game.getSecondPlayer().getScore())
                    .build();

            webSocketService.notifyGameUpdate(game.getId().toString(), username, response);
        }
    }

    public void updateGameState(Game game, String username) {
        GameStateResponse response = buildBaseGameStateResponse(game, username);
        webSocketService.notifyGameUpdate(game.getId().toString(), username, response);
    }


    private GameStateResponse buildBaseGameStateResponse(Game game, String username) {
        Player player = game.getPlayerByUsername(username);
        Player opponentPlayer = game.getOpponent(player);

        GameState state = game.getState();
        boolean opponentsClock = game.getWinner() == null && state.isInTurn(opponentPlayer)
                && state.getNextMoveTime() != null;

        CardDTO playedCard = cardMapper.toDTO(player.getPlayedCard());

        CardDTO opponentPlayedCard = cardMapper.toDTO(opponentPlayer.getPlayedCard());

        List<CardDTO> deck = cardMapper.toDTO(player.getHand());

        return GameStateResponse.builder()
                .gameId(game.getId().toString())
                .deck(deck)
                .trumpCard(cardMapper.toDTO(state.getTrumpCard()))
                .playedCard(playedCard)
                .opponentPlayedCard(opponentPlayedCard)
                .opponentPlayerCardsCount(opponentPlayer.getHand().size())
                .firstPlayerUsername(game.getFirstPlayer().getUsername())
                .firstPlayerResult(game.getFirstPlayer().getResult())
                .secondPlayerUsername(game.getSecondPlayer().getUsername())
                .secondPlayerResult(game.getSecondPlayer().getResult())
                .remainingCardsCount(state.getDeck().size())
                .isOnTurn(state.isInTurn(player))
                .isClosed(state.isClosed())
                .winnerUsername(game.getWinner() != null ? game.getWinner().getUsername() : null)
                .surrenderPlayerUsername(game.getSurrenderPlayer() != null ? game.getSurrenderPlayer().getUsername() : null)
                .bonus(player.getBonus())
                .opponentPlayerBonus(opponentPlayer.getBonus())
                .inactivityCount(player.getInactivityCount())
                .nextMoveTimeInSeconds(state.isInTurn(player) ?
                        Math.toIntExact(Duration.between(Instant.now(), state.getNextMoveTime()).getSeconds()) : null)
                .opponentTurnStartedAt(opponentsClock ? state.turnStartedAt() : null)
                .opponentDeadline(opponentsClock ? state.getNextMoveTime() : null)
                .build();
    }
}
