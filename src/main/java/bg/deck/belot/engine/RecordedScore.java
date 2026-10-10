package bg.deck.belot.engine;

/**
 * What the two teams write on the sheet for one deal.
 *
 * @param caller    the team whose bid set the contract
 * @param opponents the other one
 */
public record RecordedScore(int caller, int opponents) {

    public int total() {
        return caller + opponents;
    }
}
