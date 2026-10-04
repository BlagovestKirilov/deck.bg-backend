package bg.deck.tabla;

import bg.deck.tabla.service.TablaDiceService;
import bg.deck.tabla.engine.Dice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dice service has no dependencies, so this is a plain unit test. */
class TablaDiceServiceTest {

    private final TablaDiceService service = new TablaDiceService();

    @Test
    @DisplayName("a roll is reproducible from the seed, which is what makes it verifiable")
    void rollsAreDeterministic() {
        byte[] seed = service.newSeed();
        UUID gameId = UUID.randomUUID();

        for (int turn = 0; turn < 50; turn++) {
            Dice first = service.roll(seed, gameId, turn);
            Dice second = service.roll(seed, gameId, turn);
            assertEquals(first, second, "same seed and turn must reproduce the roll");
        }
    }

    @Test
    @DisplayName("different turns and different games give different rolls")
    void rollsVary() {
        byte[] seed = service.newSeed();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        Set<String> seen = new HashSet<>();
        for (int turn = 0; turn < 200; turn++) {
            Dice d = service.roll(seed, a, turn);
            seen.add(d.d1() + "-" + d.d2());
        }
        assertTrue(seen.size() > 20, "rolls should spread across the 36 combinations, saw " + seen.size());

        // The game id is part of the derivation, so two games do not share a sequence.
        int differences = 0;
        for (int turn = 0; turn < 50; turn++) {
            if (!service.roll(seed, a, turn).equals(service.roll(seed, b, turn))) {
                differences++;
            }
        }
        assertTrue(differences > 40, "different games should not share a roll sequence");
    }

    @Test
    @DisplayName("every die lands in 1..6")
    void diceAreInRange() {
        byte[] seed = service.newSeed();
        UUID gameId = UUID.randomUUID();
        for (int turn = 0; turn < 2000; turn++) {
            Dice d = service.roll(seed, gameId, turn);
            assertTrue(d.d1() >= 1 && d.d1() <= 6, "d1 out of range: " + d.d1());
            assertTrue(d.d2() >= 1 && d.d2() <= 6, "d2 out of range: " + d.d2());
        }
    }

    @Test
    @DisplayName("face distribution is close to uniform, so rejection sampling works")
    void distributionIsUnbiased() {
        byte[] seed = service.newSeed();
        UUID gameId = UUID.randomUUID();
        int[] counts = new int[7];
        int rolls = 60000;

        for (int turn = 0; turn < rolls; turn++) {
            Dice d = service.roll(seed, gameId, turn);
            counts[d.d1()]++;
            counts[d.d2()]++;
        }

        int expected = (rolls * 2) / 6;
        for (int face = 1; face <= 6; face++) {
            double drift = Math.abs(counts[face] - expected) / (double) expected;
            assertTrue(drift < 0.05,
                    "face " + face + " drifted " + Math.round(drift * 100) + "% from uniform");
        }
    }

    @Test
    @DisplayName("the hash commits to the seed and does not leak it")
    void hashIsACommitment() {
        byte[] seed = service.newSeed();
        String hash = service.hash(seed);

        assertEquals(64, hash.length(), "SHA-256 hex is 64 characters");
        assertEquals(hash, service.hash(seed), "hashing is stable");
        assertNotEquals(hash, service.hash(service.newSeed()), "a different seed hashes differently");
    }

    @Test
    @DisplayName("the opening roll is never a tie, since one side has to start")
    void openingRollBreaksTies() {
        for (int i = 0; i < 500; i++) {
            byte[] seed = service.newSeed();
            UUID gameId = UUID.randomUUID();
            Dice opening = service.openingRoll(seed, gameId, 0);
            assertFalse(opening.isDouble(), "opening roll must decide a starter");

            // The index actually consumed must reproduce that same roll, or the
            // turn counter would drift and later rolls would not verify.
            int used = service.openingRollIndexUsed(seed, gameId, 0);
            assertEquals(opening, service.roll(seed, gameId, used));
        }
    }
}
