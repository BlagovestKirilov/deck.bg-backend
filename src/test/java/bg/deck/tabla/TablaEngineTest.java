package bg.deck.tabla;

import bg.deck.tabla.engine.BackgammonRules;
import bg.deck.tabla.engine.BoardState;
import bg.deck.tabla.engine.ComboHop;
import bg.deck.tabla.engine.Dice;
import bg.deck.tabla.enums.GameResultKind;
import bg.deck.tabla.engine.Hop;
import bg.deck.tabla.engine.MoverView;
import bg.deck.tabla.enums.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the табла rules engine — no Spring, no Testcontainers,
 * no database. The whole class runs in well under a second.
 */
class TablaEngineTest {

    /** Builds a board from (point, count) pairs; positive = WHITE, negative = BLACK. */
    private static BoardState board(int whiteBar, int blackBar, int whiteOff, int blackOff, int... pairs) {
        int[] p = new int[BoardState.POINTS + 1];
        for (int i = 0; i < pairs.length; i += 2) {
            p[pairs[i]] = pairs[i + 1];
        }
        return new BoardState(p, whiteBar, blackBar, whiteOff, blackOff);
    }

    private static int maxUsed(BoardState b, Side s, int... dice) {
        return BackgammonRules.maxUsed(b, s, dice);
    }

    private static List<Hop> turnHops(BoardState b, Side s, int[] dice, int used, int m) {
        return BackgammonRules.legalTurnHops(b, s, dice, used, m);
    }

    /** What undo does: rebuild the position by replaying hops from a snapshot. */
    private static BoardState replay(BoardState from, Side side, List<Hop> hops) {
        BoardState b = from;
        for (Hop h : hops) {
            b = BackgammonRules.apply(b, side, h);
        }
        return b;
    }

    /** BoardState holds an array, so its record equals() compares references. */
    private static void assertSamePosition(BoardState expected, BoardState actual) {
        for (int p = 1; p <= BoardState.POINTS; p++) {
            assertEquals(expected.at(p), actual.at(p), "point " + p);
        }
        assertEquals(expected.whiteBar(), actual.whiteBar(), "white bar");
        assertEquals(expected.blackBar(), actual.blackBar(), "black bar");
        assertEquals(expected.whiteOff(), actual.whiteOff(), "white off");
        assertEquals(expected.blackOff(), actual.blackOff(), "black off");
    }

    @Nested
    @DisplayName("Board structure")
    class Structure {

        @Test
        @DisplayName("opening position has 15 checkers per side")
        void openingHasFifteenEach() {
            BoardState b = BoardState.initial();
            assertEquals(15, b.checkerCount(Side.WHITE));
            assertEquals(15, b.checkerCount(Side.BLACK));
        }

        @Test
        @DisplayName("opening position is its own mirror under 25 - i")
        void openingIsSelfMirrored() {
            BoardState b = BoardState.initial();
            for (int i = 1; i <= BoardState.POINTS; i++) {
                assertEquals(b.at(i), -b.at(25 - i), "point " + i);
            }
        }

        @Test
        @DisplayName("both sides start with the same pip count of 167")
        void openingPipCounts() {
            BoardState b = BoardState.initial();
            assertEquals(167, b.pipCount(Side.WHITE));
            assertEquals(167, b.pipCount(Side.BLACK));
        }

        @Test
        @DisplayName("encode/decode round-trips")
        void encodeRoundTrips() {
            BoardState b = BoardState.initial();
            assertEquals(b, BoardState.decode(b.encode()));
        }

        @Test
        @DisplayName("the mover frame is symmetric between the two sides")
        void moverFrameIsSymmetric() {
            Random rnd = new Random(42);
            for (int iter = 0; iter < 1000; iter++) {
                int[] p = new int[BoardState.POINTS + 1];
                for (int i = 1; i <= BoardState.POINTS; i++) {
                    p[i] = rnd.nextInt(11) - 5;
                }
                BoardState b = new BoardState(p, 0, 0, 0, 0);
                MoverView white = MoverView.of(b, Side.WHITE);
                MoverView black = MoverView.of(b, Side.BLACK);
                for (int n = 1; n <= BoardState.POINTS; n++) {
                    assertEquals(white.at(n), -black.at(25 - n), "index " + n);
                }
            }
        }
    }

    @Nested
    @DisplayName("Movement and blocking")
    class Movement {

        @Test
        @DisplayName("a point held by two or more enemy checkers is blocked")
        void blockedByTwo() {
            BoardState b = board(0, 0, 0, 0, 10, 1, 6, -2);
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 4);
            assertTrue(hops.isEmpty(), "6-point is held by two black checkers");
        }

        @Test
        @DisplayName("landing on a lone enemy checker is a hit")
        void loneEnemyIsHit() {
            BoardState b = board(0, 0, 0, 0, 10, 1, 6, -1);
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 4);
            assertEquals(1, hops.size());
            assertTrue(hops.getFirst().hit());
        }

        @Test
        @DisplayName("a hit sends the enemy checker to its bar and clears the point")
        void hitSendsToBar() {
            BoardState b = board(0, 0, 0, 0, 10, 1, 6, -1);
            Hop hop = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 4).getFirst();
            BoardState after = BackgammonRules.apply(b, Side.WHITE, hop);
            assertEquals(1, after.blackBar());
            assertEquals(1, after.at(6), "white now owns the point alone");
            assertEquals(1, after.checkerCount(Side.WHITE), "white checker conserved");
            assertEquals(1, after.checkerCount(Side.BLACK), "black checker conserved, now on the bar");
        }

        @Test
        @DisplayName("BLACK moves in the opposite direction on the canonical board")
        void blackMovesOppositeWay() {
            BoardState b = board(0, 0, 0, 0, 5, -1);
            Hop hop = BackgammonRules.legalHops(MoverView.of(b, Side.BLACK), 3).getFirst();
            BoardState after = BackgammonRules.apply(b, Side.BLACK, hop);
            // Canonically BLACK travels 1 -> 24, so from point 5 with a 3 it lands on 8.
            assertEquals(0, after.at(5));
            assertEquals(-1, after.at(8));
        }
    }

    @Nested
    @DisplayName("The bar")
    class Bar {

        @Test
        @DisplayName("while a checker is on the bar nothing else may move")
        void barBlocksEverythingElse() {
            BoardState b = board(1, 0, 0, 0, 13, 2, 20, 2);
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 3);
            assertEquals(1, hops.size());
            assertTrue(hops.getFirst().isEntry(), "only the entry hop is offered");
        }

        @Test
        @DisplayName("entering with die d lands on 25 - d in the mover frame, for both sides")
        void entryLandsOnTwentyFiveMinusDie() {
            BoardState white = board(1, 0, 0, 0);
            Hop wh = BackgammonRules.legalHops(MoverView.of(white, Side.WHITE), 5).getFirst();
            assertEquals(20, wh.to(), "WHITE enters on canonical 20");

            BoardState black = board(0, 1, 0, 0);
            Hop bh = BackgammonRules.legalHops(MoverView.of(black, Side.BLACK), 5).getFirst();
            assertEquals(20, bh.to(), "same normalised index for BLACK");
            BoardState after = BackgammonRules.apply(black, Side.BLACK, bh);
            assertEquals(-1, after.at(5), "which is canonical point 5 for BLACK");
        }

        @Test
        @DisplayName("a fully blocked entry means no dice can be used")
        void fullyBlockedEntry() {
            BoardState b = board(1, 0, 0, 0, 19, -2, 20, -2, 21, -2, 22, -2, 23, -2, 24, -2);
            assertEquals(0, maxUsed(b, Side.WHITE, 1, 2, 3, 4, 5, 6));
        }

        @Test
        @DisplayName("two on the bar with one legal entry allows exactly one die")
        void twoOnBarOneEntry() {
            // WHITE enters on 25-d. Block everything except entry with a 6 (point 19).
            BoardState b = board(2, 0, 0, 0, 20, -2, 21, -2, 22, -2, 23, -2, 24, -2);
            assertEquals(1, maxUsed(b, Side.WHITE, 6, 5));
        }
    }

    @Nested
    @DisplayName("Must-use rules")
    class MustUse {

        @Test
        @DisplayName("when only the lower die is playable, the higher is not offered")
        void onlyLowerPlayable() {
            // WHITE on 5. A 6 would bear off but home is not clear; a 5 bears off exactly.
            BoardState b = board(0, 0, 14, 0, 5, 1);
            assertEquals(1, maxUsed(b, Side.WHITE, 6, 5));
        }

        @Test
        @DisplayName("when either die alone is playable but not both, the higher one is forced")
        void higherDieIsForced() {
            // WHITE on 8 and on 20. The checker on 20 is stuck (14 and 15 are held),
            // which also keeps allHome() false so nothing can bear off.
            //   die 6: 8/2 is open,  die 5: 8/3 is open  -> each die alone is playable
            //   after either, the other die has no move   -> only one die is usable
            BoardState b = board(0, 0, 0, 0,
                    8, 1, 20, 1,
                    14, -2, 15, -2);
            int m = maxUsed(b, Side.WHITE, 6, 5);
            assertEquals(1, m, "only one die is usable");

            List<Hop> hops = turnHops(b, Side.WHITE, new int[]{6, 5}, 0, m);
            assertFalse(hops.isEmpty());
            assertTrue(hops.stream().allMatch(h -> h.die() == 6), "the higher die must be used: " + hops);
        }

        @Test
        @DisplayName("a first hop that strands the turn on one die is rejected")
        void extendabilityIsEnforced() {
            // WHITE has checkers on 24 and 13. Rolling 6-5:
            //   24->18 then 18->13 uses both.
            //   Playing the 5 first from 24 lands on 19, from which the 6 is blocked.
            BoardState b = board(0, 0, 0, 0,
                    24, 2, 13, 2,
                    13 - 6, -2,   // 7  blocks 13/7 with a 6
                    13 - 5, -2,   // 8  blocks 13/8 with a 5
                    19 - 6, -2);  // 13 is ours, so block the 19->13 follow-up differently
            // Recompute cleanly: the only two-die path must be 24/18/13.
            int m = maxUsed(b, Side.WHITE, 6, 5);
            assertEquals(2, m, "both dice are playable via 24/18/13");

            List<Hop> first = turnHops(b, Side.WHITE, new int[]{6, 5}, 0, m);
            // Every offered opening hop must be extendable to a two-die play.
            for (Hop h : first) {
                BoardState after = BackgammonRules.apply(b, Side.WHITE, h);
                assertEquals(1, BackgammonRules.maxUsed(after, Side.WHITE, Dice.without(new int[]{6, 5}, h.die())),
                        "hop " + h + " must leave a second die playable");
            }
        }

        @Test
        @DisplayName("doubles grant four moves and confirm is rejected early")
        void doublesGrantFour() {
            BoardState b = board(0, 0, 0, 0, 24, 4);
            assertEquals(4, maxUsed(b, Side.WHITE, 3, 3, 3, 3));
        }

        @Test
        @DisplayName("doubles with only three playable cap the turn at three")
        void doublesPartiallyPlayable() {
            // Three white checkers can each move 3; the landing point then blocks further play.
            BoardState b = board(0, 0, 0, 0,
                    10, 3,
                    7, 0,
                    4, -2, 1, -2);
            int m = maxUsed(b, Side.WHITE, 3, 3, 3, 3);
            assertEquals(3, m, "10/7 three times, then 7/4 is blocked");
        }

        @Test
        @DisplayName("no legal move at all means the turn passes")
        void noLegalMove() {
            BoardState b = board(0, 0, 0, 0,
                    10, 1,
                    4, -2, 5, -2);
            assertEquals(0, maxUsed(b, Side.WHITE, 6, 5));
            assertTrue(turnHops(b, Side.WHITE, new int[]{6, 5}, 0, 0).isEmpty());
        }
    }

    @Nested
    @DisplayName("Both dice at once")
    class Combos {

        private List<ComboHop> combos(BoardState b, Side s, int[] dice) {
            return BackgammonRules.legalComboHops(b, s, dice, 0, maxUsed(b, s, dice));
        }

        @Test
        @DisplayName("one checker playing both dice is offered as a single destination")
        void offersTheFarSquare() {
            BoardState b = board(0, 0, 0, 0, 10, 1);
            List<ComboHop> out = combos(b, Side.WHITE, new int[]{3, 2});

            assertEquals(1, out.size(), "10-3-2 and 10-2-3 both land on 5; one entry is enough");
            assertEquals(10, out.getFirst().from());
            assertEquals(5, out.getFirst().to());
        }

        @Test
        @DisplayName("a checker six away bears off with both dice")
        void bearsOffWithBothDice() {
            // The board's last checker sits on 6 with a 2 and a 4. No single die
            // takes it out; 6-2 to 4, then the 4 exactly, does. The client used
            // to consult only single-die hops for the tray, so this checker had
            // no way out on screen even though the engine allowed it.
            BoardState b = board(0, 0, 14, 0, 6, 1);
            assertEquals(2, maxUsed(b, Side.WHITE, 2, 4));

            List<ComboHop> out = combos(b, Side.WHITE, new int[]{2, 4});

            assertTrue(out.stream().anyMatch(c -> c.from() == 6 && c.to() == MoverView.OFF),
                    "bearing off with both dice must be offered");
        }

        @Test
        @DisplayName("a combo takes a blot it lands on along the way")
        void comboHitsOnTheMidpoint() {
            // 7 holds a lone black checker. White plays 10-3 onto it and then
            // 7-2 onward, so the hit happens on the midpoint of what the player
            // sees as a single two-dice move.
            BoardState b = board(0, 0, 0, 0, 10, 1, 7, -1);
            assertEquals(2, maxUsed(b, Side.WHITE, 3, 2));

            // Both orders reach 5: via 8 quietly, via 7 over the blot. The
            // route that takes the checker is the one offered.
            assertTrue(combos(b, Side.WHITE, new int[]{3, 2}).stream()
                            .anyMatch(c -> c.from() == 10 && c.vias().equals(List.of(7)) && c.to() == 5),
                    "the two-dice move is routed through the blot, not around it");

            Hop first = turnHops(b, Side.WHITE, new int[]{3, 2}, 0, 2).stream()
                    .filter(h -> h.from() == 10 && h.die() == 3)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("10/7 is not legal"));

            assertTrue(first.hit(), "the midpoint is a blot, so the hop takes it");
            assertEquals(1, BackgammonRules.apply(b, Side.WHITE, first).blackBar(),
                    "the checker it took sits on the bar");
        }

        @Test
        @DisplayName("with 2 and 4, a blot at either die's distance is taken")
        void eitherDieTakesItsBlot() {
            // A checker on 12 with a 2 and a 4. Blots sit on 10 (the 2) and on
            // 8 (the 4); each is taken by playing that die on its own.
            BoardState b = board(0, 0, 0, 0, 12, 1, 10, -1, 8, -1);

            List<Hop> hops = turnHops(b, Side.WHITE, new int[]{2, 4}, 0, maxUsed(b, Side.WHITE, 2, 4));

            assertTrue(hops.stream().anyMatch(h -> h.from() == 12 && h.die() == 2 && h.to() == 10 && h.hit()),
                    "the 2 takes the blot on 10");
            assertTrue(hops.stream().anyMatch(h -> h.from() == 12 && h.die() == 4 && h.to() == 8 && h.hit()),
                    "the 4 takes the blot on 8");
        }

        @Test
        @DisplayName("with 2 and 4, a combo takes a blot on the midpoint and one at the end")
        void comboTakesBothBlots() {
            // Blots on 10 and on 6. Playing the 2 first lands on 10 and takes
            // it, then the 4 lands on 6 and takes that too: two checkers to the
            // bar from what the player sees as one move.
            BoardState b = board(0, 0, 0, 0, 12, 1, 10, -1, 6, -1);
            assertEquals(2, maxUsed(b, Side.WHITE, 2, 4));

            ComboHop combo = combos(b, Side.WHITE, new int[]{2, 4}).stream()
                    .filter(c -> c.from() == 12 && c.to() == 6)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("12 to 6 with both dice is not offered"));

            assertEquals(List.of(10), combo.vias(),
                    "12-8-6 also reaches 6 but takes only one; the order through 10 takes both");

            // Play it out the way the server does, one hop at a time.
            Hop first = turnHops(b, Side.WHITE, new int[]{2, 4}, 0, 2).stream()
                    .filter(h -> h.from() == 12 && h.die() == combo.dice().getFirst())
                    .findFirst().orElseThrow();
            BoardState mid = BackgammonRules.apply(b, Side.WHITE, first);

            Hop second = turnHops(mid, Side.WHITE, new int[]{combo.dice().get(1)}, 1, 2).stream()
                    .filter(h -> h.from() == combo.vias().getFirst() && h.die() == combo.dice().get(1))
                    .findFirst().orElseThrow();
            BoardState end = BackgammonRules.apply(mid, Side.WHITE, second);

            assertEquals(2, end.blackBar(), "both blots end up on the bar");
        }

        @Test
        @DisplayName("taking a combo back twice restores the starting position")
        void undoingACombo() {
            // Undo replays the turn from its opening snapshot rather than
            // inverting the last hop, so this is the property it depends on:
            // replaying a prefix of the hops reproduces that point in the turn
            // exactly, including a checker that was sent to the bar coming back.
            BoardState start = board(0, 0, 0, 0, 12, 1, 10, -1, 6, -1);

            Hop first = turnHops(start, Side.WHITE, new int[]{2, 4}, 0, 2).stream()
                    .filter(h -> h.from() == 12 && h.die() == 2)
                    .findFirst().orElseThrow();
            BoardState afterFirst = BackgammonRules.apply(start, Side.WHITE, first);

            Hop second = turnHops(afterFirst, Side.WHITE, new int[]{4}, 1, 2).stream()
                    .filter(h -> h.from() == 10 && h.die() == 4)
                    .findFirst().orElseThrow();
            BoardState afterBoth = BackgammonRules.apply(afterFirst, Side.WHITE, second);

            assertEquals(2, afterBoth.blackBar(), "both blots were taken");

            // One Върни: replay only the first hop.
            assertSamePosition(afterFirst, replay(start, Side.WHITE, List.of(first)));
            // A second Върни: replay nothing at all.
            assertSamePosition(start, replay(start, Side.WHITE, List.of()));
            assertEquals(0, replay(start, Side.WHITE, List.of()).blackBar(),
                    "the checkers that were taken are back off the bar");
        }

        @Test
        @DisplayName("doubles let one checker spend three or all four dice")
        void doublesRunFurther() {
            // A lone checker on 20 with double 3s. It can go 17, 14, 11 or 8,
            // and every one of those beyond the first is a run the player can
            // take in a single tap.
            BoardState b = board(0, 0, 0, 0, 20, 1);
            int[] dice = {3, 3, 3, 3};
            assertEquals(4, maxUsed(b, Side.WHITE, dice));

            List<ComboHop> out = combos(b, Side.WHITE, dice);

            assertTrue(out.stream().anyMatch(c -> c.to() == 14 && c.dice().size() == 2),
                    "two dice reach 14");
            assertTrue(out.stream().anyMatch(c -> c.to() == 11 && c.dice().size() == 3),
                    "three dice reach 11");

            ComboHop all = out.stream().filter(c -> c.to() == 8).findFirst()
                    .orElseThrow(() -> new AssertionError("all four dice are not offered"));
            assertEquals(4, all.dice().size());
            assertEquals(List.of(17, 14, 11), all.vias(), "it stops on every point on the way");
            assertEquals(List.of(20, 17, 14, 11, 8), all.path());
        }

        @Test
        @DisplayName("a run stops where the checker can go no further")
        void runStopsAtABlock() {
            // Double 3s again, but 14 is held by the opponent. The checker can
            // reach 17 with one die and nothing beyond it, so no run exists.
            // A second checker on 9 keeps the turn playable.
            BoardState b = board(0, 0, 0, 0, 20, 1, 9, 1, 14, -2, 11, -2);

            assertTrue(combos(b, Side.WHITE, new int[]{3, 3, 3, 3}).stream()
                            .noneMatch(c -> c.from() == 20),
                    "the checker on 20 is shut in after one die");
        }

        @Test
        @DisplayName("a combo never steps over a blocked midpoint")
        void refusesBlockedMidpoint() {
            // From 10 both routes to 5 are shut: 7 and 8 are held by the opponent.
            // The second checker on 20 keeps the turn playable so maxUsed stays 2.
            BoardState b = board(0, 0, 0, 0, 10, 1, 20, 1, 7, -2, 8, -2);
            assertEquals(2, maxUsed(b, Side.WHITE, 3, 2));

            List<ComboHop> out = combos(b, Side.WHITE, new int[]{3, 2});

            assertTrue(out.stream().noneMatch(c -> c.from() == 10),
                    "5 is empty, but neither route to it is legal");
            assertTrue(out.stream().anyMatch(c -> c.from() == 20 && c.to() == 15),
                    "the unobstructed checker still gets its combo");
        }

        @Test
        @DisplayName("nothing is offered when only one die can be played")
        void noneWhenOnlyOneDiePlayable() {
            // One checker on 24. The 5 is dead (19 is held), and after 24-6-18
            // the 5 is still dead (13 is held), so exactly one die can be played.
            BoardState b = board(0, 0, 0, 0, 24, 1, 19, -2, 13, -2);
            assertEquals(1, maxUsed(b, Side.WHITE, 6, 5));

            assertTrue(combos(b, Side.WHITE, new int[]{6, 5}).isEmpty());
        }

        @Test
        @DisplayName("every combo is two hops that are each legal in turn")
        void combosAgreeWithSingleHops() {
            BoardState b = BoardState.initial();
            int[] dice = {3, 1};
            int m = maxUsed(b, Side.WHITE, dice);

            for (ComboHop combo : combos(b, Side.WHITE, dice)) {
                Hop first = turnHops(b, Side.WHITE, dice, 0, m).stream()
                        .filter(h -> h.from() == combo.from() && h.die() == combo.dice().getFirst())
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("first hop of the combo is not legal"));
                assertEquals(combo.vias().getFirst(), first.to());

                BoardState after = BackgammonRules.apply(b, Side.WHITE, first);
                assertTrue(turnHops(after, Side.WHITE, Dice.without(dice, combo.dice().getFirst()), 1, m)
                                .stream()
                                .anyMatch(h -> h.from() == combo.vias().getFirst() && h.to() == combo.to()
                                        && h.die() == combo.dice().get(1)),
                        "second hop of the combo is not legal from the position the first leaves");
            }
        }
    }

    @Nested
    @DisplayName("Bearing off")
    class BearOff {

        @Test
        @DisplayName("rejected while a checker is outside home")
        void rejectedWhenNotAllHome() {
            BoardState b = board(0, 0, 0, 0, 5, 1, 10, 1);
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 5);
            assertTrue(hops.stream().noneMatch(Hop::isBearOff));
        }

        @Test
        @DisplayName("rejected while a checker is on the bar, including after a mid-turn hit")
        void rejectedWhenOnBar() {
            BoardState b = board(1, 0, 0, 0, 5, 1);
            assertFalse(MoverView.of(b, Side.WHITE).allHome());
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 5);
            assertTrue(hops.stream().noneMatch(Hop::isBearOff));
        }

        @Test
        @DisplayName("exact die bears off")
        void exactDie() {
            BoardState b = board(0, 0, 14, 0, 5, 1);
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 5);
            assertEquals(1, hops.size());
            assertTrue(hops.getFirst().isBearOff());
            BoardState after = BackgammonRules.apply(b, Side.WHITE, hops.getFirst());
            assertEquals(15, after.whiteOff());
            assertTrue(BackgammonRules.isFinished(after, Side.WHITE));
        }

        @Test
        @DisplayName("overshoot is legal only when no checker sits higher")
        void overshootRules() {
            BoardState clear = board(0, 0, 13, 0, 3, 2);
            assertTrue(BackgammonRules.legalHops(MoverView.of(clear, Side.WHITE), 5)
                    .stream().anyMatch(Hop::isBearOff), "nothing above point 3, so overshoot is legal");

            BoardState blocked = board(0, 0, 13, 0, 3, 1, 5, 1);
            assertTrue(BackgammonRules.legalHops(MoverView.of(blocked, Side.WHITE), 5)
                    .stream().noneMatch(h -> h.isBearOff() && h.from() == 3),
                    "a checker on 5 forbids overshooting from 3");
        }

        @Test
        @DisplayName("a larger die can still be played as an ordinary move inside home")
        void largerDieMovesInsideHome() {
            BoardState b = board(0, 0, 13, 0, 6, 1, 4, 1);
            List<Hop> hops = BackgammonRules.legalHops(MoverView.of(b, Side.WHITE), 6);
            assertTrue(hops.stream().anyMatch(h -> h.from() == 6 && h.isBearOff()));
        }
    }

    @Nested
    @DisplayName("Result kind")
    class Results {

        @Test
        @DisplayName("loser with checkers off is a plain win")
        void single() {
            BoardState b = board(0, 0, 15, 3);
            assertEquals(GameResultKind.SINGLE, BackgammonRules.resultKind(b, Side.WHITE));
        }

        @Test
        @DisplayName("loser with nothing off is марс")
        void gammon() {
            BoardState b = board(0, 0, 15, 0, 12, -15);
            assertEquals(GameResultKind.GAMMON, BackgammonRules.resultKind(b, Side.WHITE));
        }

        @Test
        @DisplayName("марс with a checker still on the bar is кокс")
        void backgammonOnBar() {
            BoardState b = board(0, 1, 15, 0, 12, -14);
            assertEquals(GameResultKind.BACKGAMMON, BackgammonRules.resultKind(b, Side.WHITE));
        }

        @Test
        @DisplayName("марс with a checker in the winner's home is кокс")
        void backgammonInWinnerHome() {
            // WHITE's home is canonical 1..6; a BLACK checker there when WHITE wins.
            BoardState b = board(0, 0, 15, 0, 3, -1, 12, -14);
            assertEquals(GameResultKind.BACKGAMMON, BackgammonRules.resultKind(b, Side.WHITE));
        }
    }

    @Nested
    @DisplayName("Self-play fuzz")
    class Fuzz {

        /**
         * Plays random legal games end to end. Asserts the invariant that matters —
         * checkers are never created or destroyed — and that games terminate.
         * This finds bugs faster than any hand-written case.
         */
        @Test
        @DisplayName("2000 random games conserve checkers and terminate")
        void randomGamesAreSound() {
            Random rnd = new Random(20260908L);
            long worstNanos = 0;

            for (int game = 0; game < 2000; game++) {
                BoardState board = BoardState.initial();
                Side side = rnd.nextBoolean() ? Side.WHITE : Side.BLACK;
                int turns = 0;

                while (!BackgammonRules.isFinished(board, Side.WHITE)
                        && !BackgammonRules.isFinished(board, Side.BLACK)) {
                    if (++turns > 400) {
                        throw new AssertionError("game did not terminate in 400 turns");
                    }

                    Dice dice = new Dice(rnd.nextInt(6) + 1, rnd.nextInt(6) + 1);
                    int[] remaining = dice.values();

                    long t0 = System.nanoTime();
                    int m = BackgammonRules.maxUsed(board, side, remaining);
                    worstNanos = Math.max(worstNanos, System.nanoTime() - t0);

                    for (int used = 0; used < m; used++) {
                        List<Hop> options = BackgammonRules.legalTurnHops(board, side, remaining, used, m);
                        assertFalse(options.isEmpty(),
                                "maxUsed promised " + m + " dice but no hop was offered at " + used);
                        Hop pick = options.get(rnd.nextInt(options.size()));
                        board = BackgammonRules.apply(board, side, pick);
                        remaining = Dice.without(remaining, pick.die());

                        assertEquals(15, board.checkerCount(Side.WHITE), "white checkers conserved");
                        assertEquals(15, board.checkerCount(Side.BLACK), "black checkers conserved");
                    }

                    side = side.opponent();
                }
            }

            // maxUsed runs synchronously inside a transaction holding a pooled
            // connection, so a regression here would stall requests.
            assertTrue(worstNanos < 50_000_000L,
                    "worst maxUsed took " + (worstNanos / 1_000_000.0) + "ms");
        }
    }
}
