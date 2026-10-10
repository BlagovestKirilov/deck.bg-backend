package bg.deck.common.constant;

import lombok.experimental.UtilityClass;

import java.time.Duration;

@UtilityClass
public class Constants {
    public static final String NOTIFY_GAME_DESTINATION = "/topic/game/%s/%s";
    public static final String NOTIFY_GAME_SEARCH_DESTINATION = "/topic/game/%s";
    /**
     * Search topic namespaced by game, so a user queued for табла is not woken by
     * a Santase match. The legacy destination above is published to as well for
     * one release, so a client mid-deploy does not miss its match.
     */
    public static final String NOTIFY_SEARCH_BY_GAME_DESTINATION = "/topic/search/%s/%s";

    /**
     * Belot pushes a different state to every seat — each player’s own hand is
     * in it — so the destination ends in the username, and the subscribe check
     * refuses anyone listening to somebody else’s.
     *
     * <p>Keyed by player rather than by table, unlike сантасе’s: a client has to
     * subscribe before it knows which table it will be given, and belot has no
     * separate search topic to tell it. The table id is in the state anyway.
     */
    public static final String NOTIFY_BELOT_DESTINATION = "/topic/belot/%s";

    /** The one cache: the games on offer. @see bg.deck.common.config.CacheConfig */
    public static final String SERVICES_CACHE = "services";

    /** What a seat says once the account that sat in it is gone. */
    public static final String DELETED_PLAYER = "Изтрит играч";
    public static final String ROLE = "role";
    public static final String USERNAME = "username";
    public static final String BEARER = "Bearer ";
    public static final String WEB_SOCKET_ENDPOINT = "/ws-game";
    public static final String TOKEN = "token";
    public static final String TOPIC = "/topic";
    public static final String APP = "/app";
    public static final String LOCALHOST = "http://localhost:3000";
    public static final String DECK_BG = "https://deck.bg";
    public static final String DECK_BG_CONFIRM_EMAIL = "https://deck.bg/api/auth/confirm-email?token=";
    public static final String DECK_BG_SUCCESS_CONFIRMATION = "https://deck.bg/confirmation-success";
    public static final String DECK_BG_INVALID_LINK = "https://deck.bg/invalid";
    public static final String DECK_BG_EMAIL = "no.reply.deck.bg@gmail.com";
    /**
     * The sender's name. No dot, deliberately: a dot is a special character in
     * an address's display name, so a name that contains one is written as a
     * quoted string — and Gmail and several webmail clients show those quotes
     * as part of the name ("DECK.bg"), however the header was encoded on the
     * way out. Same name as the startup banner.
     */
    public static final String DECK_BG_PERSONAL = "DECK BG";
    public static final String DECK_BG_EMAIL_SUBJECT = "Потвърди своя профил в DECK.bg";
    /** The one thread account emails are sent on, so they leave in order. */
    public static final String MAIL_EXECUTOR = "mailExecutor";
    /**
     * How long a password-reset or account-deletion link works. Both are asked
     * for and acted on in the same minute, so a link that outlives the person
     * reading it is only a link someone else can find later in the mailbox.
     */
    public static final Duration LINK_VALIDITY = Duration.ofMinutes(15);
    /**
     * How long a new player's confirmation link works. Longer on purpose: that
     * one is not asked for at a moment of their choosing — someone signs up and
     * comes back to their mail later — and a quarter of an hour would lock them
     * out of the account they just made.
     */
    public static final Duration EMAIL_CONFIRMATION_VALIDITY = Duration.ofHours(24);
    public static final String EMAIL_VALIDITY = "{{VALIDITY}}";
    public static final String EMAIL_USERNAME = "{{USERNAME}}";
    public static final String EMAIL_CONFIRMATION_LINK = "{{CONFIRMATION_LINK}}";
    public static final String EMAIL_CONFIRMATION_TEMPLATE = "/templates/email-confirmation.html";
    public static final String USER = "USER";
    public static final String PROD = "prod";
    public static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    public static final String FORGOT_PASSWORD_SUBJECT = "Нова парола за DECK.bg";
    public static final String DECK_BG_FORGOT_PASSWORD = "https://deck.bg/reset-password?token=";
    public static final String FORGOT_PASSWORD_TEMPLATE = "/templates/forgot-password.html";
    public static final String DELETION_SUBJECT = "Изтриване на профила в DECK.bg";
    /**
     * The deletion link goes to a page on the site, which asks the person to
     * confirm and then POSTs the token. It used to point straight at the API
     * endpoint, which deleted the account on GET — so any mail scanner or
     * link prefetcher that opened the message (Outlook Safe Links, antivirus,
     * some mobile mail clients) destroyed the account with nobody clicking.
     */
    public static final String DECK_BG_DELETE_ACCOUNT = "https://deck.bg/confirm-deletion?token=";
    public static final String DELETION_TEMPLATE = "/templates/user-deletion.html";
}
