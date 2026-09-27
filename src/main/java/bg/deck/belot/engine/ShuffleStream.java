package bg.deck.belot.engine;

/**
 * An endless run of unbiased numbers, read off bytes somebody else derived.
 *
 * <p>This is what shuffles a deal, and it exists so that nothing in the
 * shuffle depends on {@link java.util.Random}. That class is a 48-bit linear
 * congruential generator: seeding it from a 256-bit HMAC throws away all but
 * 48 bits of the secret, and its state can be recovered from its own output.
 * Табла's dice never went near it — {@code TablaDiceService} takes its numbers
 * straight from the HMAC — and now neither does belot.
 *
 * <p>There is a second reason, and it is the one a player would care about.
 * "Provably fair" only means something if a player can actually check it. A
 * shuffle that depends on {@code java.util.Random} and
 * {@code Collections.shuffle} can only be replayed by reimplementing two
 * pieces of Java; a shuffle that is Fisher–Yates over HMAC-SHA256 bytes can be
 * replayed in ten lines of anything.
 *
 * <p>Numbers are drawn by rejection sampling. Four bytes give a value in
 * 0..2^32-1, and the top partial block is thrown away rather than folded in
 * with {@code %}, which would make the low values very slightly likelier.
 */
public final class ShuffleStream {

    private static final long TWO_32 = 1L << 32;

    private final byte[] bytes;
    private int next;

    /**
     * @param bytes the keystream, already derived; long enough that a whole
     *              deal can be shuffled out of it without running dry
     */
    public ShuffleStream(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    /** A number in {@code [0, bound)}, every value as likely as every other. */
    public int nextBelow(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }

        // The largest multiple of bound that fits in 32 bits. Anything at or
        // above it is discarded, so what remains divides evenly.
        long limit = (TWO_32 / bound) * bound;
        while (true) {
            long candidate = nextUnsigned32();
            if (candidate < limit) {
                return (int) (candidate % bound);
            }
        }
    }

    private long nextUnsigned32() {
        if (next + Integer.BYTES > bytes.length) {
            // A 32-card shuffle needs 31 draws, and the keystream is sized for
            // many times that even if every draw were rejected. Running out
            // means the caller built it wrong, and carrying on by wrapping
            // round would silently repeat the shuffle.
            throw new IllegalStateException("The shuffle ran out of bytes after " + next);
        }

        long value = 0;
        for (int i = 0; i < Integer.BYTES; i++) {
            value = (value << 8) | (bytes[next++] & 0xFFL);
        }
        return value;
    }
}
