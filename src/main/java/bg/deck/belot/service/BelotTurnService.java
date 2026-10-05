package bg.deck.belot.service;

import bg.deck.belot.config.BelotProperties;
import bg.deck.belot.engine.BidAction;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The turn clock: whose turn it is, how long is left of it, and what the table
 * does when it runs out.
 *
 * <p>A player who walks away mid-deal would otherwise leave three others at a
 * table that can never finish. The table does not forfeit for them — their
 * partner did nothing wrong — and it does not wait for ever either. It takes
 * the least consequential legal action on their behalf: a pass while the
 * bidding is on, the first legal card once it is not.
 *
 * <p>Auto-playing rather than forfeiting is also the honest reading of what
 * happened: somebody stopped answering, which is not the same as losing.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotTurnService {

    private final BelotDealService belotDealService;
    private final BelotPlayService belotPlayService;
    private final BelotProperties belotProperties;

    /** Whose turn it is, whether the deal is being bid for or played. */
    public Optional<Seat> toAct(BelotDeal deal) {
        return switch (deal.getStatus()) {
            // Before anybody bids, the deck is cut — and it is the cutter's turn.
            case BIDDING -> Optional.of(deal.isAwaitingCut() ? deal.cutter() : deal.bidding().toAct());
            case PLAYING -> Optional.of(belotPlayService.toAct(deal));
            // Nobody is to act on a hand that is over, given up on included.
            case THROWN_IN, FINISHED, ABANDONED -> Optional.empty();
        };
    }

    /**
     * When the seat now to act runs out of time.
     *
     * <p>Sent to the client so it counts the same clock down rather than one
     * of its own — a timer that disagrees with the server is worse than none.
     */
    public Optional<Instant> deadline(BelotDeal deal) {
        if (toAct(deal).isEmpty() || deal.getTurnStartedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(deal.getTurnStartedAt().plus(
                deal.isAwaitingCut() ? belotProperties.cutTimeout() : belotProperties.turnTimeout()));
    }

    /** From the cut to the first bid: the deal being shown. */
    public Duration dealPause() {
        return belotProperties.dealPause();
    }

    /** From the last card of a hand to the cut: the trick taken and the count shown. */
    public Duration handPause() {
        return belotProperties.handPause();
    }

    /**
     * Whether the trick just finished is still being taken.
     *
     * <p>The fourth card starts the leader's turn, but every screen at the
     * table is still showing that trick being swept to whoever took it. A
     * lead before that is over replaces the trick on all four screens with
     * the next one, and nobody sees who took it — so it is refused, however
     * fast the leader clicks.
     */
    public boolean isTakingTrick(BelotDeal deal, Instant now) {
        return deal.getStatus() == BelotDealStatus.PLAYING
                && deal.currentTrick().isEmpty()
                && !deal.getPlays().isEmpty()
                && deal.getTurnStartedAt() != null
                && now.isBefore(deal.getTurnStartedAt().plus(belotProperties.trickPause()));
    }

    public boolean hasRunOut(BelotDeal deal, Instant now) {
        return deadline(deal).filter(now::isAfter).isPresent();
    }

    public Duration turnTimeout() {
        return belotProperties.turnTimeout();
    }

    /**
     * Acts for the seat whose time is up.
     *
     * <p>Returns false when there was nothing to do — the turn was played
     * between the timer firing and this being called, which two instances
     * overlapping during a deploy make ordinary rather than exceptional.
     */
    public boolean actForAbsentPlayer(BelotGame game, BelotDeal deal) {
        Optional<Seat> seat = toAct(deal);
        if (seat.isEmpty() || !hasRunOut(deal, Instant.now())) {
            return false;
        }
        Seat absent = seat.get();

        if (deal.isAwaitingCut()) {
            // Anywhere in the middle half of the deck, as a hand that has not
            // chosen would cut it.
            int at = ThreadLocalRandom.current().nextInt(8, 25);
            belotDealService.cut(deal, at, Instant.now().plus(belotProperties.dealPause()));
            log.info("Belot: {} did not cut at table {}, cut at {} for them", absent, game.getId(), at);
            return true;
        }

        if (deal.getStatus() == BelotDealStatus.BIDDING) {
            // A pass is the one call that is always legal and never commits
            // their partner to anything.
            belotDealService.bid(deal, BidAction.pass(absent));
            log.info("Belot: {} ran out of time at table {}, passed for them", absent, game.getId());
            return true;
        }

        List<Card> legal = belotPlayService.legalFor(game, deal, absent);
        if (legal.isEmpty()) {
            return false;
        }
        belotPlayService.play(game, deal, absent, legal.getFirst());
        log.info("Belot: {} ran out of time at table {}, played {} for them",
                absent, game.getId(), legal.getFirst());
        return true;
    }
}
