package bg.deck.belot;

import bg.deck.belot.engine.DealRounding;
import bg.deck.belot.engine.RecordedScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RULES §8 — what goes on the score sheet.
 *
 * <p>Every number here is Blagovest's: the rules page says only "до цяло
 * число", so these examples are the rule.
 */
@DisplayName("Rounding a deal")
class BelotRoundingTest {

    @Nested
    @DisplayName("the plain cases")
    class Plain {

        @ParameterizedTest(name = "{0} against {1} is recorded {2} / {3}")
        @CsvSource({
                // Blagovest's own numbers.
                "98,  64, 10,  6",
                "94,  68,  9,  7",
                "154, 104, 15, 11",
                // A five goes down, and the sheet still adds to 16.
                "85,  77,  8,  8",
                // Nothing to argue about.
                "90,  72,  9,  7",
                "100, 62, 10,  6",
        })
        @DisplayName("nearest ten, and the two agree")
        void nearestTen(int callerPoints, int opponentPoints, int caller, int opponents) {
            RecordedScore recorded = DealRounding.split(callerPoints, opponentPoints);

            assertEquals(caller, recorded.caller());
            assertEquals(opponents, recorded.opponents());
        }
    }

    @Nested
    @DisplayName("when the two roundings do not add up")
    class Adjusted {

        @Test
        @DisplayName("both ending in 4: the caller takes the lower rounding")
        void bothEndInFour() {
            RecordedScore recorded = DealRounding.split(154, 104);

            assertEquals(15, recorded.caller());
            assertEquals(11, recorded.opponents());
            assertEquals(26, recorded.total(), "258 is 26 on the sheet");
        }

        @Test
        @DisplayName("and the same when the caller is the one behind")
        void bothEndInFourTheOtherWayRound() {
            RecordedScore recorded = DealRounding.split(104, 154);

            assertEquals(10, recorded.caller());
            assertEquals(16, recorded.opponents());
            assertEquals(26, recorded.total());
        }

        @Test
        @DisplayName("otherwise the team that took more goes up")
        void theBiggerScoreGoesUp() {
            // 155 and 103 are 258: five-down alone would record 25.
            RecordedScore recorded = DealRounding.split(155, 103);

            assertEquals(16, recorded.caller(), "the caller took more, so the caller goes up");
            assertEquals(10, recorded.opponents());
            assertEquals(26, recorded.total());
        }

        @Test
        @DisplayName("and when they come out a point over, the smaller goes down")
        void theSmallerScoreGoesDown() {
            // 86 and 76 are 162: nearest would record 9 and 8, which is 17.
            RecordedScore recorded = DealRounding.split(86, 76);

            assertEquals(9, recorded.caller());
            assertEquals(7, recorded.opponents());
            assertEquals(16, recorded.total());
        }
    }

    @Nested
    @DisplayName("always")
    class Invariants {

        /**
         * The property the whole rule exists for: whatever the split, the sheet
         * shows the deal. Every split of every deal total is checked, bonuses
         * included — a declaration or a capot simply makes the total larger.
         */
        @ParameterizedTest(name = "a deal worth {0}")
        @ValueSource(ints = {162, 258, 260, 182, 212, 348, 350})
        @DisplayName("the two recorded scores add up to the deal")
        void theSheetAddsUp(int dealTotal) {
            for (int callerPoints = 0; callerPoints <= dealTotal; callerPoints++) {
                RecordedScore recorded = DealRounding.split(callerPoints, dealTotal - callerPoints);

                assertEquals(DealRounding.nearest(dealTotal), recorded.total(),
                        "caller took " + callerPoints + " of " + dealTotal);
            }
        }

        @ParameterizedTest(name = "{0} is never more than a point out")
        @ValueSource(ints = {162, 258, 260})
        @DisplayName("neither score is moved more than one from its own rounding")
        void nobodyIsMovedFar(int dealTotal) {
            for (int callerPoints = 0; callerPoints <= dealTotal; callerPoints++) {
                int opponentPoints = dealTotal - callerPoints;
                RecordedScore recorded = DealRounding.split(callerPoints, opponentPoints);

                assertEquals(true, Math.abs(recorded.caller() - DealRounding.nearest(callerPoints)) <= 1
                                && Math.abs(recorded.opponents() - DealRounding.nearest(opponentPoints)) <= 1,
                        "caller took " + callerPoints + " of " + dealTotal);
            }
        }
    }

    @Nested
    @DisplayName("a five")
    class Fives {

        @Test
        @DisplayName("goes down")
        void fiveGoesDown() {
            assertEquals(8, DealRounding.nearest(85));
            assertEquals(7, DealRounding.nearest(75));
            assertEquals(9, DealRounding.nearest(86), "but a six goes up");
        }
    }
}
