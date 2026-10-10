package bg.deck.belot;

import bg.deck.belot.engine.DealOutcome;
import bg.deck.belot.enums.DealResult;
import bg.deck.belot.engine.DealScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RULES §8 — settling a deal: made, вътре, висящи.
 */
@DisplayName("Settling a deal")
class BelotDealScoringTest {

    /** A plain deal, nothing doubled, nothing hanging from before. */
    private static DealOutcome plain(int callerPoints, int opponentPoints) {
        return DealScorer.score(callerPoints, opponentPoints, 1, 0);
    }

    @Nested
    @DisplayName("made")
    class Made {

        @Test
        @DisplayName("the callers took more, so each side records its own")
        void eachRecordsItsOwn() {
            DealOutcome outcome = plain(90, 72);

            assertEquals(DealResult.MADE, outcome.result());
            assertEquals(9, outcome.recorded().caller());
            assertEquals(7, outcome.recorded().opponents());
            assertEquals(0, outcome.hanging());
        }

        @Test
        @DisplayName("one point in it is enough")
        void aSinglePointIsEnough() {
            assertEquals(DealResult.MADE, plain(82, 80).result());
        }
    }

    @Nested
    @DisplayName("вътре")
    class Inside {

        @Test
        @DisplayName("the others took more, so they record everything and the callers nothing")
        void theOthersTakeTheLot() {
            DealOutcome outcome = plain(80, 82);

            assertEquals(DealResult.INSIDE, outcome.result());
            assertEquals(0, outcome.recorded().caller());
            assertEquals(16, outcome.recorded().opponents(), "162 in the deal, all of it theirs");
            assertEquals(0, outcome.hanging());
        }

        @Test
        @DisplayName("bonuses go the same way — the whole deal is theirs")
        void bonusesGoTooLegacy() {
            // The callers held a quarte, and still went down: 50 of those 130
            // points are theirs, and they lose them with the rest.
            DealOutcome outcome = plain(130, 132);

            assertEquals(26, outcome.recorded().opponents(), "262 between them, recorded as 26");
            assertEquals(0, outcome.recorded().caller());
        }
    }

    @Nested
    @DisplayName("висящи")
    class Hanging {

        @Test
        @DisplayName("level: the callers record nothing and their points wait")
        void levelLeavesThePointsOnTheTable() {
            DealOutcome outcome = plain(81, 81);

            assertEquals(DealResult.HANGING, outcome.result());
            assertEquals(0, outcome.recorded().caller(), "«не записва точките си»");
            assertEquals(8, outcome.recorded().opponents(), "the others record theirs as usual");
            assertEquals(8, outcome.hanging(), "and the callers' eight waits for the next deal");
        }

        @Test
        @DisplayName("what hangs is picked up by whoever makes the next deal")
        void theNextWinnerCollectsIt() {
            DealOutcome hung = plain(81, 81);
            DealOutcome next = DealScorer.score(90, 72, 1, hung.hanging());

            assertEquals(17, next.recorded().caller(), "nine of their own and the eight that was waiting");
            assertEquals(7, next.recorded().opponents());
            assertEquals(0, next.hanging(), "nothing is left over");
        }

        @Test
        @DisplayName("and it keeps waiting through another level deal")
        void hangingOnHanging() {
            DealOutcome first = plain(81, 81);
            DealOutcome second = DealScorer.score(81, 81, 1, first.hanging());

            assertEquals(16, second.hanging(), "eight from each deal, still on the table");
        }
    }

    @Nested
    @DisplayName("contra")
    class Doubled {

        @Test
        @DisplayName("made, the callers take everything, doubled")
        void doubled() {
            DealOutcome outcome = DealScorer.score(90, 72, 2, 0);

            assertEquals(32, outcome.recorded().caller(), "162 is 16, doubled");
            assertEquals(0, outcome.recorded().opponents(), "and the others record nothing");
        }

        @Test
        @DisplayName("and quadrupled after a recontra")
        void quadrupled() {
            DealOutcome outcome = DealScorer.score(90, 72, 4, 0);

            assertEquals(64, outcome.recorded().caller());
            assertEquals(0, outcome.recorded().opponents());
        }

        @Test
        @DisplayName("made and doubled, the callers collect what was hanging as well")
        void doubledCollectsTheHanging() {
            DealOutcome outcome = DealScorer.score(90, 72, 2, 8);

            assertEquals(40, outcome.recorded().caller(), "32 for the deal and the 8 that was waiting");
        }

        @Test
        @DisplayName("and a deal that goes down doubles for the others")
        void insideDoubled() {
            DealOutcome outcome = DealScorer.score(80, 82, 2, 0);

            assertEquals(32, outcome.recorded().opponents(), "16 for the deal, doubled");
            assertEquals(0, outcome.recorded().caller());
        }

        @Test
        @DisplayName("points that hang while doubled carry forward doubled")
        void hangingStaysDoubled() {
            DealOutcome outcome = DealScorer.score(81, 81, 2, 0);

            assertEquals(16, outcome.hanging(), "«всички точки (удвоени или учетворени) остават»");
        }
    }

    /* ------------- the rules that were open, now answered -------------- */

    @Nested
    @DisplayName("contra, and where the doubling lands")
    class Doubling {

        /** ANSWERED (OPEN 16). */
        @Test
        @DisplayName("the doubling is of what goes on the sheet, not of the card points")
        void theDoublingComesAfterTheRounding() {
            // "Резултатът се удвоява" — the result is what is written down, so
            // the rounding happens first and the doubling is applied to it. A
            // deal of 155 rounds down to 15 and doubles to 30; doubling the
            // points first would give 310, which rounds to 31.
            DealOutcome plainDeal = DealScorer.score(70, 85, 1, 0);
            DealOutcome doubled = DealScorer.score(70, 85, 2, 0);

            assertEquals(15, plainDeal.recorded().opponents());
            assertEquals(30, doubled.recorded().opponents(),
                    "twice the fifteen on the sheet, not the rounding of twice the points");
        }

        /** ANSWERED (OPEN 16), the other multiplier. */
        @Test
        @DisplayName("a recontra quadruples the same number")
        void recontraQuadruplesIt() {
            assertEquals(60, DealScorer.score(70, 85, 4, 0).recorded().opponents());
        }
    }

    @Nested
    @DisplayName("висящи — who ends up with them")
    class HangingCollected {

        /** ANSWERED (OPEN 17). */
        @Test
        @DisplayName("whoever records the next deal takes what was hanging, defenders included")
        void whoCollectsAfterAFailedDeal() {
            // 16 hanging from a tied deal, and the next one goes вътре: the
            // defenders record the whole deal, so the hanging points go on the
            // sheet with it. They are not the callers' to keep by failing.
            DealOutcome outcome = DealScorer.score(80, 82, 1, 16);

            assertEquals(DealResult.INSIDE, outcome.result());
            assertEquals(0, outcome.recorded().caller());
            assertEquals(32, outcome.recorded().opponents(),
                    "16 for the deal and the 16 that were hanging");
            assertEquals(0, outcome.hanging(), "nothing is left hanging after it is collected");
        }

        /** ANSWERED (OPEN 17), the straightforward half. */
        @Test
        @DisplayName("and the callers take them when the callers make it")
        void theCallersCollectWhenTheyMakeIt() {
            DealOutcome outcome = DealScorer.score(90, 72, 1, 16);

            assertEquals(DealResult.MADE, outcome.result());
            assertEquals(25, outcome.recorded().caller(), "9 for the deal and the 16 hanging");
            assertEquals(7, outcome.recorded().opponents(), "the other side takes only its own");
        }
    }
}
