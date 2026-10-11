package bg.deck.common.model.response;

import java.util.List;

/**
 * One page of the admin list of players.
 *
 * <p>Its own shape rather than Spring's {@code Page}, whose JSON is an
 * implementation detail that Spring Data itself warns may change.
 *
 * @param players    this page's accounts
 * @param page       which page, counting from 0
 * @param size       how many accounts a page holds
 * @param total      how many accounts match, on every page together
 * @param totalPages how many pages they fill
 */
public record AdminPlayerPage(List<AdminPlayerRow> players, int page, int size, long total, int totalPages) {
}
