package bg.deck.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How tokens are signed and how long they live. Read once at startup and
 * registered by {@link SecurityConfig}.
 *
 * @param secretKey         the Base64 HMAC key every token is signed with
 * @param expiration        how long an access token lives, in milliseconds
 * @param refreshExpiration how long a refresh token lives, in milliseconds
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secretKey, long expiration, long refreshExpiration) {

    /**
     * Without the key. A record prints every component, and the key is the
     * one thing that must never reach a log: whoever holds it can sign a
     * token for any account.
     */
    @Override
    public String toString() {
        return "JwtProperties[secretKey=<hidden>, expiration=" + expiration
                + ", refreshExpiration=" + refreshExpiration + "]";
    }
}
