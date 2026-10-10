package bg.deck.belot.service;

import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Bidding;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Dealing;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.model.BelotBid;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.enums.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.repository.BelotDealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deals: the only place {@link BelotDealRepository} is spoken to.
 *
 * <p>A deal is a number and a dealer. Everything else about it — who holds
 * which eight cards, whose turn it is to speak, what the contract stands at —
 * is derived: the cards from the game's seed and the deal's number, the bidding
 * by replaying the turns that were taken. Nothing is written that could later
 * contradict the shuffle the table was shown the hash of.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotDealService {

    private final BelotDealRepository belotDealRepository;
    private final BelotSeedService belotSeedService;

    /** The deal in progress at this table, if there is one. */
    @Transactional(readOnly = true)
    public Optional<BelotDeal> current(BelotGame game) {
        return belotDealRepository.findFirstByGameOrderByDealNumberDesc(game);
    }

    /**
     * Deals the next hand.
     *
     * <p>The first is dealt by whoever the table named when it filled; after
     * that the deal moves one seat along, thrown-in hands included — RULES §4
     * gives the next dealer the redeal.
     */
    @Transactional
    public BelotDeal dealNext(BelotGame game) {
        Optional<BelotDeal> previous = current(game);
        int number = previous.map(deal -> deal.getDealNumber() + 1).orElse(1);
        Seat dealer = previous.map(deal -> deal.getDealerSeat().next()).orElseGet(game::getDealerSeat);

        BelotDeal deal = new BelotDeal(game, number, dealer);
        deal.setTurnStartedAt(Instant.now());
        log.info("Belot: table {} deals hand {}, {} dealing", game.getId(), number, dealer);
        return belotDealRepository.save(deal);
    }

    /**
     * One turn of the bidding.
     *
     * <p>Legality is {@link Bidding}'s to judge, and it is asked rather than
     * second-guessed here: whose turn it is, what beats what, who may contra.
     * This method's own job is what happens to the deal afterwards.
     *
     * @throws IllegalArgumentException if it is not a legal thing to say
     */
    @Transactional
    public BelotDeal bid(BelotDeal deal, BidAction action) {
        Bidding before = deal.bidding();
        // Throws when it is not this seat's turn, or not a legal call.
        Bidding after = before.apply(action);

        deal.add(new BelotBid(deal.nextOrdinal(), action));
        // The next seat’s clock starts the moment this one stops.
        deal.setTurnStartedAt(Instant.now());

        if (after.isThrownIn()) {
            deal.setStatus(BelotDealStatus.THROWN_IN);
            log.info("Belot: hand {} thrown in, nobody bid", deal.getDealNumber());
        } else if (after.isFinished()) {
            deal.setStatus(BelotDealStatus.PLAYING);
            deal.setContract(after.highestBid());
            deal.setDeclarerSeat(after.bidder());
            deal.setDoubling(after.doubling());
            log.info("Belot: hand {} is {} by {}{}", deal.getDealNumber(), after.highestBid(),
                    after.bidder(), after.doubling().multiplier() > 1 ? " " + after.doubling() : "");
        }

        return belotDealRepository.save(deal);
    }

    /**
     * Every deal with a turn on: being bid for or being played.
     *
     * <p>A deal that is finished, thrown in or given up on is nobody’s move.
     */
    @Transactional(readOnly = true)
    public List<BelotDeal> inProgress() {
        return belotDealRepository.findByStatusIn(List.of(BelotDealStatus.BIDDING, BelotDealStatus.PLAYING));
    }

    /**
     * One deal, read fresh.
     *
     * <p>For the turn timer, which holds a deal's id from an earlier
     * transaction: a detached entity cannot load its bids or its plays, so
     * acting on one has to start by reading it again inside the transaction
     * that will do the acting.
     */
    @Transactional(readOnly = true)
    public Optional<BelotDeal> byId(UUID id) {
        return belotDealRepository.findById(id);
    }

    /**
     * Gives up on the hand in progress, without counting it.
     *
     * <p>Called when a player concedes the game. The hand is left where it
     * stopped: nothing goes on the sheet for it, and it drops out of
     * {@link #inProgress} so the turn clock lets it alone.
     */
    @Transactional
    public void abandon(BelotDeal deal) {
        deal.setStatus(BelotDealStatus.ABANDONED);
        deal.setTurnStartedAt(null);
        belotDealRepository.save(deal);
    }

    /**
     * Holds a freshly dealt hand for the cut: nobody bids until the player on
     * the dealer's left has cut, and their clock for it starts at {@code from}.
     */
    @Transactional
    public void awaitCut(BelotDeal deal, Instant from) {
        deal.setCutAt(null);
        deal.setTurnStartedAt(from);
        belotDealRepository.save(deal);
    }

    /**
     * The deck is cut, here; the bidding clock starts at {@code biddingFrom},
     * once the deal has had time to be shown.
     *
     * @throws IllegalArgumentException if the deck is not waiting to be cut
     */
    @Transactional
    public void cut(BelotDeal deal, int at, Instant biddingFrom) {
        if (!deal.isAwaitingCut()) {
            throw new IllegalArgumentException("Deal " + deal.getId() + " is not waiting to be cut");
        }
        deal.setCutAt(at);
        deal.setTurnStartedAt(biddingFrom);
        belotDealRepository.save(deal);
    }

    /**
     * Says пас for every seat in turn that has nothing else it may say.
     *
     * <p>Over a recontra on всичко коз, say, the only call left to the others
     * is пас, and making three people press it — or wait out their clock —
     * for a call that was never theirs to choose is the table standing still
     * for nothing. It gives nothing away: what a seat may call depends on
     * the bidding, which everybody has heard, and not on its cards.
     */
    @Transactional
    public void passWhereThereIsNoChoice(BelotDeal deal) {
        while (deal.getStatus() == BelotDealStatus.BIDDING) {
            List<BidAction> legal = deal.bidding().legalActions();
            if (legal.size() != 1) {
                return;
            }
            bid(deal, legal.getFirst());
        }
    }

    /** Every hand at this table, oldest first. */
    @Transactional(readOnly = true)
    public List<BelotDeal> history(BelotGame game) {
        return belotDealRepository.findByGameOrderByDealNumberAsc(game);
    }

    /** Writes a deal and the bids and cards hanging off it. */
    @Transactional
    public BelotDeal save(BelotDeal deal) {
        return belotDealRepository.save(deal);
    }

    /**
     * The eight cards a seat holds in this deal, dealt again from the seed.
     *
     * <p>Derived on every read. Two deals of the same number at the same table
     * are the same deal, and there is no row that could say otherwise.
     */
    public Map<Seat, List<Card>> hands(BelotGame game, BelotDeal deal) {
        return Dealing.deal(
                Dealing.shuffled(belotSeedService.shuffleFor(game.getServerSeed(), deal.getDealNumber())),
                deal.getDealerSeat());
    }

    /**
     * What one seat may see of its own hand right now.
     *
     * <p>Five cards while the bidding is on, which is what the table has been
     * dealt at that point, and all eight once a contract is named. The other
     * three exist in the shuffle either way — they are simply not shown, the
     * way the last three packets are not yet on the table.
     */
    public List<Card> visibleHand(BelotGame game, BelotDeal deal, Seat seat) {
        List<Card> hand = hands(game, deal).get(seat);
        return deal.getStatus() == BelotDealStatus.BIDDING ? Dealing.beforeBidding(hand) : List.copyOf(hand);
    }
}
