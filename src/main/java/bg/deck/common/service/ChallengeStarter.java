package bg.deck.common.service;

import java.util.UUID;

/**
 * Starts a game between two friends when a challenge is accepted.
 *
 * <p>Implemented by each game that can be played by challenge, in its own
 * package: {@code common} holds the challenge and knows no game, the game
 * knows how to seat two players at a new table of its own.
 */
public interface ChallengeStarter {

    /** The game's code as the catalogue spells it. */
    String code();

    /**
     * Seats the two at a new game that counts for neither — no rating, no
     * record — and tells both screens.
     *
     * @return the new game's id
     */
    UUID startFriendly(String challenger, String challenged);
}
