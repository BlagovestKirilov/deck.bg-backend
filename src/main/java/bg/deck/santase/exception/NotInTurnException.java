package bg.deck.santase.exception;

import bg.deck.common.exception.GameRuleException;

import static bg.deck.common.constant.ExceptionConstants.PLAYER_NOT_IN_TURN;

public class NotInTurnException extends GameRuleException {
    public NotInTurnException(String username) {
        super(String.format(PLAYER_NOT_IN_TURN, username));
    }
}
