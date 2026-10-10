package bg.deck.santase.exception;

import bg.deck.common.exception.GameRuleException;

import static bg.deck.common.constant.ExceptionConstants.CARD_NOT_PLAYABLE;

public class CardNotPlayableException extends GameRuleException {
    public CardNotPlayableException() {
        super(CARD_NOT_PLAYABLE);
    }
}
