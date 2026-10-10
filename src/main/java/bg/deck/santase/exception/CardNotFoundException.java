package bg.deck.santase.exception;

import bg.deck.common.exception.GameRuleException;

import static bg.deck.common.constant.ExceptionConstants.CARD_NOT_FOUND;

public class CardNotFoundException extends GameRuleException {
    public CardNotFoundException(String username) {
        super(String.format(CARD_NOT_FOUND, username));
    }
}
