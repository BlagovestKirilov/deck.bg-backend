package bg.deck.common.model.request;

import bg.deck.common.enums.AccountRole;
import bg.deck.common.enums.Scope;

/**
 * Which players the admin list shows, from the query string. Every part is
 * optional: no filter at all is every account.
 *
 * @param username part of the name, any case
 * @param role     USER or ADMIN
 * @param scope    PUBLIC or BETA
 */
public record AdminPlayerFilter(String username, AccountRole role, Scope scope) {
}
