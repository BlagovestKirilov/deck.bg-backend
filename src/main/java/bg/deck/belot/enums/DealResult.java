package bg.deck.belot.enums;

/** How a deal ended for the team that called the contract. RULES §8. */
public enum DealResult {

    /** They took more than the others: each side records its own. */
    MADE,
    /** Вътре — the others took more, and record everything. */
    INSIDE,
    /** Висящи — level, so the caller's points wait for the next deal. */
    HANGING
}
