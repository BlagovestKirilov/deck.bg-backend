package bg.deck.santase.exception;

import static bg.deck.common.constant.ExceptionConstants.PLAYER_NOT_IN_TURN;

public class NotInTurnException extends RuntimeException {
    public NotInTurnException(String username) {
        super(String.format(PLAYER_NOT_IN_TURN, username));
    }
}
