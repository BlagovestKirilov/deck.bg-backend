package bg.deck.common.util;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A short, one-way name for a link token, safe to write in a log.
 *
 * <p>The token in a password-reset or deletion link is the credential: whoever
 * reads it can use it for as long as the link lives. Logging it puts an account
 * takeover in every log aggregator, backup and screenshot of a terminal. A
 * fingerprint identifies the same link across several lines — attempted, found,
 * refused — without being usable on any of them.
 *
 * <p>Twelve hex characters of SHA-256. Short enough to read, far too wide to
 * collide in a log, and the hash cannot be walked back to a random UUID.
 */
@UtilityClass
public class TokenFingerprint {

    private static final int LENGTH = 12;

    public static String of(UUID token) {
        if (token == null) {
            return "none";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // Every JVM has SHA-256; if this one does not, say so rather than
            // fall back to printing the token itself.
            return "unhashed";
        }
    }
}
