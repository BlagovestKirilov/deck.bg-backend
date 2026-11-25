package bg.deck.common.model.event;

import java.util.List;

/**
 * These players sat down to a game or got up from one.
 *
 * <p>Published by each game when a game starts and when it is over, so that
 * their friends' lists stop showing them as playing — or start to — without
 * waiting for a screen to close. A player who stays on the result screen keeps
 * the same socket, so the socket alone does not say it.
 *
 * @param usernames the players at that table
 */
public record PlayingChanged(List<String> usernames) {
}
