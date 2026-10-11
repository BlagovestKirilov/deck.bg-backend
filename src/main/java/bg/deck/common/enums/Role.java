package bg.deck.common.enums;

/**
 * What an account may do. One per account: a user plays; an admin plays and
 * also runs the site from /admin.
 *
 * <p>Nothing in the application grants {@link #ROLE_ADMIN} — it is set by
 * hand in the database, so a stolen session can never make another admin.
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}