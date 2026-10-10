package bg.deck.tabla.service;

import bg.deck.tabla.engine.Dice;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Provably fair dice.
 *
 * <p>Табла players are famously suspicious of dice, so rolls are committed
 * rather than merely random. At game start the server generates a secret seed
 * and publishes only its SHA-256. Every roll is derived deterministically from
 * that seed plus the turn index; the seed itself is revealed when the game ends.
 *
 * <p>That gives both properties that matter: a player cannot predict a roll
 * (they do not have the seed), and the server cannot retroactively pick a
 * favourable one (the hash was committed before any roll happened).
 */
@Service
public class TablaDiceService {

    /**
     * Deliberately {@code new SecureRandom()} and not {@code getInstanceStrong()},
     * which blocks on entropy on a small VM.
     */
    private final SecureRandom random = new SecureRandom();

    private static final String HMAC = "HmacSHA256";
    /**
     * 256 is not divisible by 6, so a naive {@code % 6} would hand values 1..4 a
     * slightly higher probability. Rejecting the top partial block removes the bias.
     */
    private static final int REJECT_AT = 252;

    public byte[] newSeed() {
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        return seed;
    }

    public String hash(byte[] seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(seed));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** The roll for one turn, derived from the committed seed. */
    public Dice roll(byte[] seed, UUID gameId, int turnIndex) {
        byte[] mac = mac(seed, gameId + ":" + turnIndex);
        int first = dieFrom(mac, 0);
        int second = dieFrom(mac, 1);
        return new Dice(first, second);
    }

    /**
     * Opening roll: each side rolls one die and the higher moves first, playing
     * both dice as their first turn. Ties are re-rolled by bumping the index.
     */
    public Dice openingRoll(byte[] seed, UUID gameId, int startIndex) {
        int index = startIndex;
        while (true) {
            Dice dice = roll(seed, gameId, index);
            if (!dice.isDouble()) {
                return dice;
            }
            index++;
        }
    }

    /** How many indices the opening roll consumed, so turnIndex stays monotonic. */
    public int openingRollIndexUsed(byte[] seed, UUID gameId, int startIndex) {
        int index = startIndex;
        while (roll(seed, gameId, index).isDouble()) {
            index++;
        }
        return index;
    }

    private int dieFrom(byte[] mac, int slot) {
        // Walk forward through the digest until a byte falls in an unbiased block.
        for (int i = slot; i < mac.length; i += 2) {
            int b = mac[i] & 0xFF;
            if (b < REJECT_AT) {
                return (b % 6) + 1;
            }
        }
        // Astronomically unlikely; fall back to the last byte rather than loop forever.
        return ((mac[mac.length - 1] & 0xFF) % 6) + 1;
    }

    private byte[] mac(byte[] seed, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(seed, HMAC));
            return mac.doFinal(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not derive dice", e);
        }
    }
}
