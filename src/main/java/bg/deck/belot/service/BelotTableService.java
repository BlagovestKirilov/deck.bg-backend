package bg.deck.belot.service;

import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.BelotForfeit;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.enums.BelotGameStatus;
import bg.deck.belot.model.BelotMatchmaking;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tables: the only place {@link BelotGameRepository} is spoken to.
 *
 * <p>Belot needs four, so a table is made once and filled three times more.
 * Somebody looking for a game takes the oldest seat going, and when the fourth
 * sits down the table starts.
 *
 * <p>Seats are handed out in playing order — north, west, south, east — which
 * puts the first and third arrivals against the second and fourth. Nobody
 * chooses partners, and nobody waits for a foursome to assemble itself.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotTableService {

    private final BelotGameRepository belotGameRepository;
    private final BelotMatchmakingService belotMatchmakingService;
    private final BelotSeedService belotSeedService;

    /**
     * Sits this player down: back at their own table if they have one, at the
     * oldest table short of players otherwise, or at a new one.
     *
     * <p>One player at a time, enforced by the database. Everything below is
     * read-then-write — look for a table with room, open one if there is none
     * — and four people pressing Търси in the same second all read "no table
     * with room" and all open one. That is not a rare interleaving: it is what
     * happens every time a table fills up at once, and it put four players at
     * three tables the first time belot was played by four people.
     */
    @Transactional
    public BelotGame join(String username) {
        takeTheMatchmakingLock();

        Optional<BelotGame> existing = belotGameRepository.findUnfinishedGameOf(username);
        if (existing.isPresent()) {
            // Rejoining is simply finding them where they were.
            return existing.get();
        }

        BelotGame table = oldestTableWithRoom().orElseGet(this::openTable);
        Seat seat = table.freeSeats().getFirst();
        table.add(new BelotSeat(seat, username));

        if (table.isFull()) {
            table.setStatus(BelotGameStatus.PLAYING);
            // The dealer of the first hand is the last to sit down, so the
            // first to speak is the one who has been waiting longest.
            table.setDealerSeat(seat);
            log.info("Belot: table {} is full, {} deals first", table.getId(), seat);
        }
        return belotGameRepository.save(table);
    }

    /** The table this player is at, if any. */
    @Transactional(readOnly = true)
    public Optional<BelotGame> tableOf(String username) {
        return belotGameRepository.findUnfinishedGameOf(username);
    }

    @Transactional(readOnly = true)
    public Optional<BelotGame> find(UUID id) {
        return belotGameRepository.findById(id);
    }

    @Transactional
    public BelotGame save(BelotGame game) {
        return belotGameRepository.save(game);
    }

    /**
     * Gets this player up from a table that is still waiting for its fourth.
     *
     * <p>Only a table that has not started: once the cards are out, leaving is
     * a surrender and goes through {@link #concede}. A table left with nobody
     * at it is not kept — it would only be found again as the oldest table
     * with room, by somebody who would then wait at it alone.
     *
     * @return the table they got up from, while somebody is still sitting at
     *         it, so the others can be told there is a free seat again
     */
    @Transactional
    public Optional<BelotGame> leaveWaiting(String username) {
        // Asked on every closed socket, most of them nobody's at a waiting
        // table: the lock is only taken for one who is, and the table read
        // again under it, since the fourth may have sat down in between.
        if (!isWaiting(username)) {
            return Optional.empty();
        }
        takeTheMatchmakingLock();
        Optional<BelotGame> seated = belotGameRepository.findUnfinishedGameOf(username);
        if (seated.isEmpty() || seated.get().getStatus() != BelotGameStatus.WAITING) {
            return Optional.empty();
        }

        BelotGame table = seated.get();
        table.getSeats().removeIf(seat -> seat.getUsername().equals(username));
        log.info("Belot: {} got up from table {} before it started, {} still seated",
                username, table.getId(), table.getSeats().size());

        if (table.getSeats().isEmpty()) {
            belotGameRepository.delete(table);
            return Optional.empty();
        }
        return Optional.of(belotGameRepository.save(table));
    }

    private boolean isWaiting(String username) {
        return belotGameRepository.findUnfinishedGameOf(username)
                .filter(game -> game.getStatus() == BelotGameStatus.WAITING)
                .isPresent();
    }

    private Optional<BelotGame> oldestTableWithRoom() {
        List<BelotGame> waiting = belotGameRepository.findByStatusOrderByCreatedAtAsc(BelotGameStatus.WAITING);
        return waiting.stream().filter(game -> !game.isFull()).findFirst();
    }

    /**
     * Blocks until whoever else is being seated has been.
     *
     * <p>Released when this transaction commits, so the next player in reads
     * a table that already has the last one sitting at it. See
     * {@link BelotMatchmaking} for why the lock is a row of its own, and
     * {@link BelotMatchmakingService} for why creating that row needs a
     * transaction of its own.
     */
    private void takeTheMatchmakingLock() {
        if (belotMatchmakingService.lock()) {
            return;
        }

        // Only on a database the changeset has not reached, which in practice
        // means a test. Created in a transaction of its own, so the players
        // who lose the race to create it still have a working transaction to
        // be seated in — this one, which was suspended while that happened.
        try {
            belotMatchmakingService.ensureRowExists();
        } catch (DataIntegrityViolationException raced) {
            log.debug("Belot: another thread created the matchmaking lock row first");
        }

        belotMatchmakingService.lock();
    }

    private BelotGame openTable() {
        BelotGame table = new BelotGame();
        byte[] seed = belotSeedService.newSeed();
        table.setServerSeed(seed);
        // Committed before anyone sits down, let alone before a card is dealt.
        table.setServerSeedHash(belotSeedService.hash(seed));
        return table;
    }

    /**
     * One player gives up, and the game goes to the other pair.
     *
     * <p>A concession binds the partner. There is no way for it not to: belot
     * is scored per pair, the sheet has two columns, and a game cannot end for
     * two of the four and go on for the other two. So the client says out loud
     * whose game is being given up before it asks for this.
     *
     * <p>The score stands as it was. A conceded game is not a 151, and nothing
     * is invented to make it look like one — what is recorded is who won.
     */
    @Transactional
    public void concede(BelotGame game, BelotSeat seat, BelotForfeit how) {
        game.setWinnerTeam(seat.team().opponent());
        game.setStatus(BelotGameStatus.FINISHED);
        game.setForfeit(how);
        game.setForfeitedBy(seat.getUsername());
        belotGameRepository.save(game);

        log.info("Belot: {} {} table {}, so it goes to {} ({}-{})",
                seat.getUsername(), how == BelotForfeit.INACTIVITY ? "ran out of time three times at" : "conceded",
                game.getId(), game.getWinnerTeam(), game.getNorthSouthScore(), game.getEastWestScore());
    }

    /**
     * One more turn the table had to take for this seat.
     *
     * @return how many that makes, over the whole game
     */
    @Transactional
    public int missedTurn(BelotGame game, Seat seat) {
        BelotSeat missed = game.getSeats().stream()
                .filter(taken -> taken.getSeat() == seat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + seat + " at table " + game.getId()));
        int missedTurns = missed.missTurn();
        belotGameRepository.save(game);
        log.info("Belot: {} at table {} has now missed {} turn(s)", missed.getUsername(), game.getId(), missedTurns);
        return missedTurns;
    }

    /**
     * Renames every seat a deleted account sat in.
     *
     * <p>The seats stay. A finished game still has to say four people were
     * at it, and a game their partner played should not lose its record
     * because the opponent closed their account. What goes is the name.
     *
     * @return how many seats were renamed
     */
    @Transactional
    public int anonymise(String username) {
        List<BelotGame> games = belotGameRepository.findGamesOf(username);

        int renamed = 0;
        for (BelotGame game : games) {
            for (BelotSeat seat : game.getSeats()) {
                if (seat.getUsername().equals(username)) {
                    seat.setUsername(Constants.DELETED_PLAYER);
                    renamed++;
                }
            }
            // A game they forfeited names them a second time.
            if (username.equals(game.getForfeitedBy())) {
                game.setForfeitedBy(Constants.DELETED_PLAYER);
            }
            belotGameRepository.save(game);
        }
        return renamed;
    }
}
