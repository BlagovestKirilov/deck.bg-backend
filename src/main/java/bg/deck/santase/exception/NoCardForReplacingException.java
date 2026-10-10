package bg.deck.santase.exception;

import bg.deck.common.exception.GameRuleException;

import static bg.deck.common.constant.ExceptionConstants.CARD_NOT_FOUND_FOR_REPLACING;

public class NoCardForReplacingException extends GameRuleException {
    public NoCardForReplacingException(String username) {
        super(String.format(CARD_NOT_FOUND_FOR_REPLACING, username));
    }
}
