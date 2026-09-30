# Belot — the rules this service implements

Source: <https://belot.bg/belot/rules/>, read 2026-09-24. Where the page is
silent, the gap is marked **❓ OPEN** and must be answered before the code that
depends on it is written — a guess here is a scoring bug that reads to players
as cheating.

Each rule below is meant to become a test. The section numbers are referenced
from `BUILD.md`.

---

## 1. Table

- 4 players, 2 teams, partners opposite each other.
- Dealing and play run **counter-clockwise** (обратно на часовниковата стрелка).
- 32 cards: 7, 8, 9, 10, J, Q, K, A in four suits.

## 2. Card order

Two orders, chosen per contract:

| | Order, high to low |
|---|---|
| **Trump** | J · 9 · A · 10 · K · Q · 8 · 7 |
| **Plain** | A · 10 · K · Q · J · 9 · 8 · 7 |

- **Suit contract** — the named suit uses the trump order, the other three use the plain order.
- **All trumps (всички козове)** — every suit uses the trump order.
- **No trumps (без козове)** — every suit uses the plain order.

## 3. Card points

| Card | In a trump suit | In a plain suit |
|---|---|---|
| J | **20** | 2 |
| 9 | **14** | 0 |
| A | 11 | 11 |
| 10 | 10 | 10 |
| K | 4 | 4 |
| Q | 3 | 3 |
| 8, 7 | 0 | 0 |

Plus **10 for the last trick** (последните десет).

### Deal totals — the engine must hit these exactly

| Contract | Total |
|---|---|
| Suit | **162** |
| All trumps | **258** |
| No trumps | **260** |

Two of these are arithmetic: a suit contract is 62 (trump suit) + 3 × 30 + 10 =
162; all trumps is 4 × 62 + 10 = 258. No trumps is 4 × 30 + 10 = 130 — **half of
260**, so the page's figure only works if every point in a no-trump deal is
doubled, the last trick included.

> **ANSWERED — every point in a no-trump deal counts double**, the last trick
> included: 2 × 120 + 2 × 10 = 260. An ace is 22 there, a ten 20. Implemented in
> `CardPoints.multiplier`.

## 4. Dealing

1. Deal **3 cards**, then **2 cards**, to each player — 5 in hand.
2. Bidding (§5).
3. The dealer then deals **3 more** to each player — 8 in hand.
4. If all four pass in the first round, the next player deals a fresh hand.

## 5. Bidding

- First to speak: **the player to the dealer's right** (пръв обявява играчът в дясно от раздаващия).
- Each bid must be higher than the last. Ascending order:

  `pass < ♣ < ♦ < ♥ < ♠ < no trumps < all trumps`

> **ANSWERED 2 — the order above is right.** The page's own list is garbled in
> translation, but its Bulgarian reads "Спатия < Каро < Купа < Пика < Без Коз <
> Всичко Коз", which is the usual Bulgarian order and what the engine has.

- Bidding ends after **three consecutive passes**.
- **Contra** — an opponent of the last bid doubles the deal's score. **Recontra**
  by the bidding side quadruples it.

> **ANSWERED 3 — bidding continues, and a raise clears the contra.** The page
> never says a contra ends the auction; what it does say is "наддаването
> приключва, когато трима поредни играчи обявят «пас»", and a contra is not a
> pass. So the auction runs on, and a contra aimed at a contract that has since
> been outbid does not survive it. `BelotBiddingTest.openThreeRaisingOverAContra`.

## 6. Playing a trick

- **Follow the led suit if you can.** When that suit is played by the trump
  order — the trump suit, or any suit in all trumps — you must also **go higher**
  than what is on the table if you can (качване), whoever holds the trick.
- If you cannot follow **and the trick currently belongs to an opponent**, you
  must trump (цака).
- If an opponent has already trumped, you must **overtrump** if able.
- **If your partner is winning the trick, there is no obligation to trump** —
  the rule applies only "ако взятката до момента принадлежи на противника".
- In **no trumps**, only following suit is required; there is nothing to trump with.

> **ANSWERED 4 — a partner winning the trick frees you from trumping, not from
> raising.** The page states the condition once, for the case where you cannot
> follow: "ако играчът не притежава карта от искания цвят и взятката до момента
> принадлежи на противника, трябва да играе коз". So with your partner winning
> you need not trump, in any contract. This answer first read the same
> condition as lifting *every* obligation, raising included, so in all trumps
> you could play under your partner's card in the led suit; 12 corrected that —
> качване holds whoever is winning. `BelotRulesTableTest.Obligations`.

> **ANSWERED 12 — yes, always: качване.** When the led suit is played by the
> trump order and you can follow, you must play higher than what is on the
> table if you hold something higher — whoever is holding the trick, your
> partner included. The page is silent; the answer came from the table. In all
> trumps that is every suit; in no trumps it is none, and you only follow. The
> engine first had it only while an opponent held the trick, borrowed from the
> trumping rule, which let a seven of trumps go under a partner's queen with the
> jack, ace and king in the hand. `BelotTrickTest.Raising`.
>
> **ANSWERED 13 — you may discard.** "В случай че няма по-висок коз, може да
> изиграе произволна карта." Nobody is made to waste a trump on a trick already
> lost. Note this is the opposite of the answer to 12: you must beat what you
> can beat, and you are free when you cannot.
> `BelotTrickTest.undertrumpingIsNotForced`.

## 7. Declarations (анонси)

Declared when playing your **first card of the deal**.

| Combination | Points |
|---|---|
| 3 in sequence (терца) | 20 |
| 4 in sequence (кварта) | 50 |
| 5+ in sequence (квинта) | 100 |
| Four 10 / Q / K / A (каре) | 100 |
| Four 9s | 150 |
| Four J | 200 |
| **Belote** — K + Q of the trump suit | 20 |

Rules:

- **Only the team with the single highest sequence scores its sequences** —
  "премии за тях си записва само отборът, обявил най-висок такъв".
- Equal length is decided by the **starting card**; if still equal, **all
  sequence bonuses are cancelled** for both teams.
- **No trumps: declarations are forbidden**, except the last trick and capot.
- All trumps: declarations are normal.

> **ANSWERED 5 — separately, and a card may serve only one of them.** Fours are
> weighed against fours and sequences against sequences, so a four does not beat
> a sequence out of the scoring. But "ако една и съща карта участва едновременно
> в каре и поредица (терца, кварта, квинта), играчът избира кое от двете да
> обяви" — four nines and 7 8 9 of spades share the nine of spades, and only one
> of them may have it. Declarations here are read off the hand rather than
> announced, so the choice is made the way a player would make it: the most
> valuable first, and anything needing a card already spoken for is dropped.
> `Declarations.chosen`, `BelotRulesTableTest.aCardCountsOnce`.
>
> **ANSWERED 6 — yes, always.** A belote is two named cards of the trump suit
> and is not in the contest at all. `BelotRulesTableTest.beloteIsIndependent`.
>
> **ANSWERED 7 — J > 9 > A > 10 > K > Q**, which is the points the page gives
> (200, 150, 100) with the trump order breaking the tie among the hundreds.
>
> **ANSWERED 8 — yes in a suit contract.** Only no trumps forbids them: "при
> игра на «Без коз» играчите нямат право да обявяват притежаваните от тях
> комбинации".
>
> **ANSWERED 14 — still 100.** The page stops at five and gives nothing beyond
> it, so a run of six, seven or eight is a quinte and worth what a quinte is
> worth. It is one run, not a quinte plus a terz.
>
> **ANSWERED 15 — one per suit that holds both, so up to four.** In all trumps
> every suit is a trump suit and a belot may be announced in any of them.
> `Declarations.belotes`.

## 8. Scoring a deal

- **Capot (капо)** — one team takes all eight tricks: **+90**.
- The contracting team **makes** the contract if it scores strictly more than
  the opponents. Each team then records its own points ÷ 10, rounded.
- **Going down (вътре)** — the contracting team fails; the **opponents record
  everything**, both teams' points, ÷ 10.
- **Hanging (висящи)** — the two sides tie. The contracting team records
  nothing; its points **carry to whoever wins the next deal**. The opponents
  record theirs.
- **Contra / recontra** double or quadruple everything, bonuses included. Points
  that hang while doubled carry forward still doubled.

> **ANSWERED — the two scores are rounded together, so the sheet adds up.**
> Each goes to its nearest ten with a **five going down** (85 is 8). If the two
> then fall a point short of the deal, one comes up: when **both end in 4** the
> calling team takes the lower rounding and the other the higher (154 and 104
> are 15 and 11); otherwise **the team that took more** goes up (155 and 103 are
> 16 and 10). A point over, and the team that took fewer goes down (86 and 76
> are 9 and 7). `DealRounding`, checked over every possible split of every deal
> total.

## 9. Ending the game

- A game ends when a team reaches **151** or more; higher total wins.
- **"С капо не се излиза"** — a team cannot finish on a capot deal: if the
  winning team reached 151+ with a capot, **one more deal is played**.

> **ANSWERED 10 — the higher total.** "Ако и двата отбора едновременно преминат
> тази граница, то печели този от тях, който има повече точки." Level on the
> line, another deal is played: a game of belot is not left drawn.
>
> **ANSWERED 11 — the extra deal must be a deal that was actually played and
> was not itself a capot.** "Изключва се раздаване, в което всички са обявили
> пас и раздаване завършило с капо." Both fall out for free: an all-pass hand is
> thrown in and dealt again, so it is never scored and `GameScorer` never sees
> it, and a second capot hits the same rule as the first and calls for another
> deal. `BelotGameScoringTest.TheExtraDeal`.

> **ANSWERED 16 — the doubling is of what goes on the sheet.** "Резултатът се
> удвоява" — the result is the number recorded, so the rounding happens first
> and the multiplier is applied to it. 85 rounds to 8 and doubles to 16, where
> doubling the points first would give 170 and round to 17.
> `BelotDealScoringTest.Doubling`.
>
> **ANSWERED 17 — whoever records the deal takes what was hanging.** When the
> next deal goes вътре the defenders record everything, and the hanging points
> go on the sheet with it. They are not the callers' to keep by failing.
> `BelotDealScoringTest.HangingCollected`.

## 10. Worked examples to turn into tests

Fill these in once the OPEN questions are answered; they are the acceptance
tests for the engine.

| # | Situation | Expected |
|---|---|---|
| 1 | Suit ♠, contractor takes 90, opponents 72 | contract made — record 9 / 7 (pending §8 rounding) |
| 2 | Suit ♠, contractor 80, opponents 82 | вътре — opponents record 162 ÷ 10 |
| 3 | Suit ♠, both 81 | висящи — contractor records nothing, carries 81 |
| 4 | All trumps, one team takes every trick | 258 + 90 capot |
| 5 | Contra, contractor goes down | opponents record (162 × 2) ÷ 10 |
| 6 | Both teams hold a terz, equal length, equal top card | no sequence bonus for either |
| 7 | No trumps, player holds K+Q of a suit | nothing — declarations forbidden |
