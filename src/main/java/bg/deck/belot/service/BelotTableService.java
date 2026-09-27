package bg.deck.belot.service;

import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.BelotSeat;
import bg.deck.belot.repository.BelotGameRepository;
import bg.deck.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
    private final BelotSeedService belotSeedService;

    /**
     * Sits this player down: back at their own table if they have one, at the
     * oldest table short of players otherwise, or at a new one.
     */
    @Transactional
    public BelotGame join(String username) {
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

    private Optional<BelotGame> oldestTableWithRoom() {
        List<BelotGame> waiting = belotGameRepository.findByStatusOrderByCreatedAtAsc(BelotGameStatus.WAITING);
        return waiting.stream().filter(game -> !game.isFull()).findFirst();
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
            belotGameRepository.save(game);
        }
        return renamed;
    }
}
