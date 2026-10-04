package bg.deck.tabla.model.response;

import bg.deck.tabla.model.dto.HopDTO;
import bg.deck.tabla.model.dto.ComboHopDTO;
import bg.deck.tabla.model.dto.OpeningThrowDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * A табла position as one player sees it.
 *
 * <p>{@code legalHops} is the important field: the server enumerates every legal
 * move, so the client never re-implements the rules and the two can never
 * disagree about "use both dice", the higher-die rule or bearing off. It is
 * naturally empty for the player who is not on turn.
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TablaStateResponse(
        String gameId,
        String gameType,

        String firstPlayerUsername,
        String secondPlayerUsername,

        /** WHITE or BLACK — which side this recipient plays. */
        String mySide,

        /** 24 entries, canonical numbering; positive = WHITE checkers. */
        List<Integer> points,

        int myBar,
        int opponentBar,
        int myOff,
        int opponentOff,

        int myPipCount,
        int opponentPipCount,

        @JsonProperty("isOnTurn")
        boolean isOnTurn,

        Integer die1,
        Integer die2,
        List<Integer> remainingDice,

        int maxDiceUsable,
        int usedDiceCount,
        boolean mustConfirm,
        boolean noMovesAvailable,

        List<HopDTO> legalHops,
        /** Destinations reachable by playing both dice with one checker. */
        List<ComboHopDTO> comboHops,
        List<HopDTO> pendingHops,

        String winnerUsername,
        String surrenderPlayerUsername,
        /** SINGLE, GAMMON (марс) or BACKGAMMON (кокс). Display only. */
        String resultKind,

        int inactivityCount,
        Integer nextMoveTimeInSeconds,

        /** Nobody has started yet: both players are throwing one die each. */
        boolean openingPhase,

        /**
         * Finished throws of the opening roll, ties included — during the opening,
         * and while the starter plays the opening dice. Null after that.
         */
        List<OpeningThrowDTO> openingThrows,

        /** This player's die of the opening throw in progress, once thrown. */
        Integer openingMine,

        /** The opponent's die of the opening throw in progress, once thrown. */
        Integer openingOpponent,

        /** Published from move one so the dice can be verified afterwards. */
        String serverSeedHash,
        /** Revealed only once the game is finished. */
        String serverSeed
) {
}
