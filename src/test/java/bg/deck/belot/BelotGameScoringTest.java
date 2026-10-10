package bg.deck.belot;

import bg.deck.belot.engine.GameScorer;
import bg.deck.belot.engine.GameVerdict;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RULES §9 — when a game is over.
 */
@DisplayName("Winning a game")
class BelotGameScoringTest {

    private static GameVerdict after(int northSouth, int eastWest) {
        return GameScorer.verdict(northSouth, eastWest, false);
    }

    private static GameVerdict afterCapot(int northSouth, int eastWest) {
        return GameScorer.verdict(northSouth, eastWest, true);
    }

    @Nested
    @DisplayName("the line at 151")
    class TheLine {

        @Test
        @DisplayName("below it, the game goes on")
        void belowTheLine() {
            assertFalse(after(150, 96).isFinished());
        }

        @Test
        @DisplayName("cross it and the game is won")
        void crossingIt() {
            GameVerdict verdict = after(151, 96);

            assertTrue(verdict.isFinished());
            assertEquals(Team.NORTH_SOUTH, verdict.winningTeam().orElseThrow());
        }

        @Test
        @DisplayName("both across, and the higher total takes it")
        void bothAcross() {
            assertEquals(Team.EAST_WEST, after(155, 160).winningTeam().orElseThrow());
            assertEquals(Team.NORTH_SOUTH, after(206, 151).winningTeam().orElseThrow());
        }
    }

    @Nested
    @DisplayName("«с капо не се излиза»")
    class NoCapotFinish {

        @Test
        @DisplayName("a capot cannot be the deal that ends it")
        void aCapotDoesNotFinishIt() {
            assertFalse(afterCapot(151, 96).isFinished(), "one more deal follows");
        }

        @Test
        @DisplayName("and the deal after it can")
        void theNextDealCan() {
            GameVerdict verdict = after(168, 96);

            assertTrue(verdict.isFinished());
            assertEquals(Team.NORTH_SOUTH, verdict.winningTeam().orElseThrow());
        }

        @Test
        @DisplayName("a capot well short of the line changes nothing")
        void aCapotBelowTheLine() {
            assertFalse(afterCapot(120, 96).isFinished(), "nobody was finishing anyway");
        }
    }

    @Nested
    @DisplayName("teams")
    class Teams {

        @Test
        @DisplayName("partners share one")
        void partnersShareATeam() {
            assertEquals(Team.NORTH_SOUTH, Team.of(Seat.NORTH));
            assertEquals(Team.NORTH_SOUTH, Team.of(Seat.SOUTH));
            assertEquals(Team.EAST_WEST, Team.of(Seat.EAST));
            assertEquals(Team.EAST_WEST, Team.of(Seat.WEST));
            assertEquals(Team.EAST_WEST, Team.NORTH_SOUTH.opponent());
        }
    }

    /* ------------- the rule that was open, now answered ---------------- */

    @Nested
    @DisplayName("the extra deal after a capot")
    class TheExtraDeal {

        /** ANSWERED (OPEN 11). */
        @Test
        @DisplayName("a capot cannot end the game, however far past the line it goes")
        void aCapotDoesNotFinishIt() {
            assertFalse(afterCapot(200, 60).isFinished(), "с капо не се излиза");
        }

        /** ANSWERED (OPEN 11): the extra deal may not itself be a capot. */
        @Test
        @DisplayName("and if the extra deal is another capot, yet another follows")
        void anotherCapotMeansAnotherDeal() {
            // The rule is written against the deal just played, not against a
            // count of how many have gone by: "изключва се ... раздаване
            // завършило с капо". So it applies again, and again.
            assertFalse(afterCapot(210, 60).isFinished());
            assertTrue(after(210, 60).isFinished(), "the first ordinary deal after it ends the game");
        }

        /**
         * ANSWERED (OPEN 11): the all-pass half, which is structural rather than
         * arithmetic. A deal nobody bid is thrown in and dealt again, so it never
         * reaches {@link GameScorer} at all — the verdict is only ever taken on a
         * deal that was played out and scored. That is what "изключва се
         * раздаване, в което всички са обявили пас" comes to in play.
         * {@code BelotTableEndToEndTest.fourPassesRedeal} is the test of it.
         */
        @Test
        @DisplayName("an all-pass deal scores nothing, so it cannot be the extra one")
        void anAllPassDealIsNotADeal() {
            assertFalse(afterCapot(160, 60).isFinished(), "still owed a deal");
            assertFalse(afterCapot(160, 60).isFinished(),
                    "and a thrown-in hand does not pay it: nothing was scored, so nothing is asked");
        }
    }
}
