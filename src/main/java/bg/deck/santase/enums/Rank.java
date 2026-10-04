package bg.deck.santase.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Rank {
    NINE(0), JACK(2), QUEEN(3), KING(4), TEN(10), ACE(11);

    private final int points;
}
