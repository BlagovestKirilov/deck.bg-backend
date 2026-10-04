package bg.deck.santase.exception;

import static bg.deck.common.constant.ExceptionConstants.CARD_NOT_PLAYABLE;

public class CardNotPlayableException extends RuntimeException {
    public CardNotPlayableException() {
        super(CARD_NOT_PLAYABLE);
    }
}
