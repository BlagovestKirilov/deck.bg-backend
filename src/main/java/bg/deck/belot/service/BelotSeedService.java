package bg.deck.belot.service;

import bg.deck.belot.engine.ShuffleStream;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The secret a table's shuffles come from, and the promise made about it.
 *
 * <p>A game starts with a random seed the server keeps and a hash of it the
 * table is shown. Every deal is shuffled from that seed and the deal's number,
 * so no shuffle can be chosen after the fact: the seed was committed to before
 * a card was dealt. At the end the seed is published and anyone can replay every
 * deal from it.
 *
 * <p>The same arrangement as табла's dice, for the same reason.
 */
@Log4j2
@Service
public class BelotSeedService {

    private static final String HMAC = "HmacSHA256";
    private static final int SEED_BYTES = 32;

    /**
     * Eight HMAC blocks, 256 bytes, against the 124 a 32-card Fisher–Yates
     * needs when nothing is rejected. Rejection is rare — a draw below 32 keeps
     * all but a vanishing fraction of the 32-bit range — so this is room to
     * spare rather than a budget.
     */
    private static final int KEYSTREAM_BLOCKS = 8;

    /**
     * Not {@code getInstanceStrong()}: on a small Linux box that blocks on the
     * entropy pool, and this is a card game, not a key ceremony.
     */
    private final SecureRandom random = new SecureRandom();

    public byte[] newSeed() {
        byte[] seed = new byte[SEED_BYTES];
        random.nextBytes(seed);
        return seed;
    }

    /** What the table is shown before the first deal. */
    public String hash(byte[] seed) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }

    /**
     * The keystream for one deal: the same seed and number always give the
     * same order of cards, and no other number gives it away.
     *
     * <p>Several HMAC blocks, numbered, so there is plenty for a whole deal
     * even if rejection sampling throws most of it away. The numbering is
     * inside the message rather than a counter mode of our own, so a player
     * verifying the published seed only needs HMAC-SHA256 and this naming.
     *
     * <p>It used to squeeze the first eight bytes into a {@code long} and hand
     * that to {@link Random}, which keeps 48 bits of it and is reversible from
     * its own output. See {@link ShuffleStream}.
     */
    public ShuffleStream shuffleFor(byte[] seed, int dealNumber) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(seed, HMAC));

            byte[] stream = new byte[KEYSTREAM_BLOCKS * mac.getMacLength()];
            for (int block = 0; block < KEYSTREAM_BLOCKS; block++) {
                byte[] digest = mac.doFinal(
                        ("deal-" + dealNumber + "/" + block).getBytes(StandardCharsets.UTF_8));
                System.arraycopy(digest, 0, stream, block * digest.length, digest.length);
            }
            return new ShuffleStream(stream);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not derive a shuffle", e);
        }
    }
}
