package bg.deck.common.enums;

/**
 * An account's role as the admin page names it — USER or ADMIN — rather than
 * as Spring Security stores it ({@link Role}, with its ROLE_ prefix).
 */
public enum AccountRole {
    USER(Role.ROLE_USER),
    ADMIN(Role.ROLE_ADMIN);

    private final Role role;

    AccountRole(Role role) {
        this.role = role;
    }

    /** The stored role this one stands for. */
    public Role role() {
        return role;
    }

    public static AccountRole of(Role role) {
        return role == Role.ROLE_ADMIN ? ADMIN : USER;
    }
}
