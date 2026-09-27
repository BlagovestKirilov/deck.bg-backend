package bg.deck.belot.service;

import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.DealOutcome;
import bg.deck.belot.engine.DealPoints;
import bg.deck.belot.engine.DealScorer;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.DeclarationScoring;
import bg.deck.belot.engine.Declarations;
import bg.deck.belot.engine.GameScorer;
import bg.deck.belot.engine.LegalMoves;
import bg.deck.belot.engine.PlayedDeal;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Team;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.engine.TrickResolver;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.BelotPlay;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The tricks: whose turn it is, what they may play, and what the deal comes to
 * when the last card is down.
 *
 * <p>Holds no repository. It works on a {@link BelotDeal} the caller has, and
 * the rules it enforces are the engine's — {@link LegalMoves} decides what may
 * be played, {@link TrickResolver} who took the trick, {@link DealScorer} what
 * it was worth. Nothing here re-decides any of that; what it adds is the order
 * those questions are asked in, and writing the answer down.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class BelotPlayService {

    private final BelotDealService belotDealService;

    /**
     * Whose turn it is to play.
     *
     * <p>The first trick is led by the seat on the dealer's right — the one
     * that opened the bidding — and every trick after it by whoever took the
     * one before. Mid-trick it simply goes round.
     */
    public Seat toAct(BelotDeal deal) {
        Trick trick = deal.currentTrick();
        if (!trick.isEmpty()) {
            return trick.plays().getLast().seat().next();
        }
        return deal.lastTrickWinner().orElseGet(() -> deal.getDealerSeat().next());
    }

    /** What this seat holds: what it was dealt, less what it has played. */
    public List<Card> handOf(BelotGame game, BelotDeal deal, Seat seat) {
        List<Card> hand = new ArrayList<>(belotDealService.hands(game, deal).get(seat));
        hand.removeAll(deal.playedBy(seat));
        return List.copyOf(hand);
    }

    /**
     * The cards this seat may legally play right now.
     *
     * <p>Empty when it is not their turn, which is what the client is sent: a
     * player is never offered a card the server would refuse.
     */
    public List<Card> legalFor(BelotGame game, BelotDeal deal, Seat seat) {
        if (deal.getStatus() != BelotDealStatus.PLAYING || toAct(deal) != seat) {
            return List.of();
        }
        return LegalMoves.of(handOf(game, deal, seat), deal.currentTrick(), seat, deal.getContract());
    }

    /**
     * Plays one card.
     *
     * <p>Refused unless it is that seat's turn and the card is both in their
     * hand and legal — the hand comes from the seed, so a card nobody was
     * dealt cannot be played by claiming it.
     *
     * @throws IllegalArgumentException if the card cannot be played now
     */
    public BelotDeal play(BelotGame game, BelotDeal deal, Seat seat, Card card) {
        List<Card> legal = legalFor(game, deal, seat);
        if (!legal.contains(card)) {
            throw new IllegalArgumentException(seat + " cannot play " + card + "; legal: " + legal);
        }

        deal.add(new BelotPlay(deal.currentTrickNumber(), deal.nextPlaceInTrick(), seat, card));
        deal.setTurnStartedAt(Instant.now());

        if (deal.isPlayedOut()) {
            settle(game, deal);
        }
        return belotDealService.save(deal);
    }

    /**
     * The deal is over: count it, write it onto the score sheet, and see
     * whether that ended the game.
     */
    private void settle(BelotGame game, BelotDeal deal) {
        Contract contract = deal.getContract();
        PlayedDeal played = playedDeal(deal, contract);
        Map<Seat, List<Card>> dealt = belotDealService.hands(game, deal);

        Map<Team, List<Declaration>> declared = declarations(dealt, contract);
        Map<Team, Integer> points = DealPoints.total(played, contract,
                declared.get(Team.NORTH_SOUTH), declared.get(Team.EAST_WEST));

        Team caller = Team.of(deal.getDeclarerSeat());
        Team defenders = caller.opponent();
        DealOutcome outcome = DealScorer.score(
                points.get(caller), points.get(defenders), deal.getDoubling().multiplier(),
                game.getHangingPoints());

        game.addScore(caller, outcome.recorded().caller());
        game.addScore(defenders, outcome.recorded().opponents());
        game.setHangingPoints(outcome.hanging());

        deal.setStatus(BelotDealStatus.FINISHED);
        deal.setCallerPoints(points.get(caller));
        deal.setOpponentPoints(points.get(defenders));
        deal.setCallerScore(outcome.recorded().caller());
        deal.setOpponentScore(outcome.recorded().opponents());
        deal.setResult(outcome.result());

        log.info("Belot: hand {} is {} — {} {}, {} {}, {} hanging", deal.getDealNumber(), outcome.result(),
                caller, outcome.recorded().caller(), defenders, outcome.recorded().opponents(), outcome.hanging());

        finishIfWon(game, played);
    }

    /** 151, and nobody wins on a deal that was a capot — RULES §9. */
    private void finishIfWon(BelotGame game, PlayedDeal played) {
        var verdict = GameScorer.verdict(
                game.getNorthSouthScore(), game.getEastWestScore(), played.capotBy() != null);

        verdict.winningTeam().ifPresent(winner -> {
            game.setWinnerTeam(winner);
            game.setStatus(BelotGameStatus.FINISHED);
            log.info("Belot: table {} goes to {} ({}–{})", game.getId(), winner,
                    game.getNorthSouthScore(), game.getEastWestScore());
        });
    }

    private PlayedDeal playedDeal(BelotDeal deal, Contract contract) {
        List<Trick> tricks = deal.tricks();
        List<Seat> winners = tricks.stream()
                .map(trick -> TrickResolver.winning(trick, contract).orElseThrow().seat())
                .toList();
        return new PlayedDeal(tricks, winners);
    }

    /**
     * What each side holds that is worth announcing.
     *
     * <p>Found in the hands rather than asked for. A declaration is in the
     * cards, and the cards are in the seed, so there is nothing for a player
     * to forget to claim and nothing for the server to take their word about.
     */
    /**
     * What each seat holds that is worth announcing, by seat.
     *
     * <p>The dealt hand, not what is left of it: a sequence announced on the
     * first trick is still that player’s sequence when the cards have been
     * played away.
     */
    public Map<Seat, List<Declaration>> declarationsBySeat(BelotGame game, BelotDeal deal) {
        Map<Seat, List<Declaration>> bySeat = new EnumMap<>(Seat.class);
        belotDealService.hands(game, deal).forEach(
                (seat, hand) -> bySeat.put(seat, Declarations.in(hand, deal.getContract())));
        return bySeat;
    }

    /** What one side scores from its announcements, once the other’s is weighed. */
    public int declarationPoints(Map<Seat, List<Declaration>> bySeat, Team team) {
        return DeclarationScoring.scoreFor(ofTeam(bySeat, team), ofTeam(bySeat, team.opponent()));
    }

    private static List<Declaration> ofTeam(Map<Seat, List<Declaration>> bySeat, Team team) {
        return bySeat.entrySet().stream()
                .filter(held -> Team.of(held.getKey()) == team)
                .flatMap(held -> held.getValue().stream())
                .toList();
    }

    private Map<Team, List<Declaration>> declarations(Map<Seat, List<Card>> hands, Contract contract) {
        Map<Team, List<Declaration>> byTeam = new EnumMap<>(Team.class);
        byTeam.put(Team.NORTH_SOUTH, new ArrayList<>());
        byTeam.put(Team.EAST_WEST, new ArrayList<>());

        hands.forEach((seat, hand) -> byTeam.get(Team.of(seat)).addAll(Declarations.in(hand, contract)));
        return byTeam;
    }
}
