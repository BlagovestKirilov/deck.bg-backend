package bg.deck.service;

import bg.deck.enums.Rank;
import bg.deck.model.Game;
import bg.deck.model.Player;
import bg.deck.model.UserGameStats;
import bg.deck.util.RankLadder;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class RankingService {

    @Transactional
    public void updateRankingAfterGame(Game game) {

        Player winner = game.getWinner();
        Player loser = game.getOpponent(winner);

        // Rating is per game type: Santase results never move a табла rating.
        UserGameStats winnerStats = winner.getUser().statsFor(game.getGameType());
        UserGameStats loserStats = loser.getUser().statsFor(game.getGameType());

        int winnerDelta = calculateEloDelta(winnerStats.getRating(),
                loserStats.getRating(), true, winnerStats.totalGames());

        int loserDelta = calculateEloDelta(loserStats.getRating(),
                winnerStats.getRating(), false, loserStats.totalGames());

        winnerStats.setRating(winnerStats.getRating() + winnerDelta);
        loserStats.setRating(loserStats.getRating() + loserDelta);

        winnerStats.setRank(resolveRank(winnerStats));
        loserStats.setRank(resolveRank(loserStats));
    }

    private int calculateEloDelta(int playerRating, int opponentRating, boolean win, int gamesPlayed) {
        int kFactor = RankLadder.kFactor(gamesPlayed);

        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - playerRating) / 400.0));

        int result = win ? 1 : 0;

        return (int) Math.round(kFactor * (result - expected));
    }

    private Rank resolveRank(UserGameStats stats) {
        return RankLadder.rankFor(stats.getRating(), stats.totalGames());
    }
}
