package bg.deck.santase.model.response;

import bg.deck.santase.model.dto.CardDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStateResponse(
        String gameId,
        List<CardDTO> deck,
        CardDTO trumpCard,
        CardDTO playedCard,
        CardDTO opponentPlayedCard,
        int opponentPlayerCardsCount,
        int remainingCardsCount,
        String firstPlayerUsername,
        int firstPlayerResult,
        String secondPlayerUsername,
        int secondPlayerResult,
        @JsonProperty("isOnTurn")
        boolean isOnTurn,
        @JsonProperty("isClosed")
        boolean isClosed,
        String winnerUsername,
        String trickWinnerUsername,
        /**
         * Who takes the trick whose two cards are on the table — sent with
         * those two cards, before the trick is taken. Not trickWinnerUsername,
         * which is the deal's winner and opens its result.
         */
        String trickTakenBy,
        String surrenderPlayerUsername,
        int trickFirstPlayerScore,
        int trickSecondPlayerScore,
        Integer bonus,
        Integer opponentPlayerBonus,
        int inactivityCount,
        Integer nextMoveTimeInSeconds,
        /**
         * The opponent's turn, while it is theirs: when it began and when it
         * runs out, so the waiting player can watch it burn down. Moments
         * rather than seconds, so a reload shows the same bar.
         */
        Instant opponentTurnStartedAt,
        Instant opponentDeadline
) {
}
