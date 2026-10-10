package bg.deck.santase.exception;

import bg.deck.common.exception.GameRuleException;

import static bg.deck.common.constant.ExceptionConstants.PLAYER_NOT_FIRST_IN_TURN;

public class NotFirstInTurnException extends GameRuleException {
    public NotFirstInTurnException(String username) {
        super(String.format(PLAYER_NOT_FIRST_IN_TURN, username));
    }
}
