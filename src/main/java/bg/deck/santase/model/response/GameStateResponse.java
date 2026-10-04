package bg.deck.santase.model.response;

import bg.deck.santase.model.dto.CardDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

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
        String surrenderPlayerUsername,
        int trickFirstPlayerScore,
        int trickSecondPlayerScore,
        Integer bonus,
        Integer opponentPlayerBonus,
        int inactivityCount,
        Integer nextMoveTimeInSeconds
) {
}
