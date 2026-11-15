package bg.deck.belot.service;

import bg.deck.belot.engine.Card;
import bg.deck.belot.enums.Contract;
import bg.deck.belot.engine.DealOutcome;
import bg.deck.belot.engine.DealPoints;
import bg.deck.belot.engine.DealScorer;
import bg.deck.belot.engine.Declaration;
import bg.deck.belot.engine.DeclarationScoring;
import bg.deck.belot.engine.Declarations;
import bg.deck.belot.engine.GameScorer;
import bg.deck.belot.engine.LegalMoves;
import bg.deck.belot.engine.PlayedDeal;
import bg.deck.belot.enums.Rank;
import bg.deck.belot.enums.Seat;
import bg.deck.belot.enums.Suit;
import bg.deck.belot.enums.Team;
import bg.deck.belot.engine.Trick;
import bg.deck.belot.engine.TrickResolver;
import bg.deck.belot.model.BelotDeal;
import bg.deck.belot.enums.BelotDealStatus;
import bg.deck.belot.model.BelotGame;
import bg.deck.belot.enums.BelotGameStatus;
import bg.deck.belot.model.BelotPlay;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final BelotStatsService belotStatsService;

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
        return play(game, deal, seat, card, null, null);
    }

    /**
     * Plays one card, with what its player chose to say as they played it.
     *
     * @param announce on a player's first card: which of {@link #announcements}
     *                 they chose, by its key. Null takes the richest, which is
     *                 what the table's clock picks for a player who ran out
     * @param belote   on a card of {@link #beloteCards}: false to play it
     *                 without saying the belote. Null says it
     * @throws IllegalArgumentException if the card cannot be played now, or
     *                                  the announcement is not one this hand has
     */
    public BelotDeal play(BelotGame game, BelotDeal deal, Seat seat, Card card, String announce, Boolean belote) {
        List<Card> legal = legalFor(game, deal, seat);
        if (!legal.contains(card)) {
            throw new IllegalArgumentException(seat + " cannot play " + card + "; legal: " + legal);
        }

        BelotPlay play = new BelotPlay(deal.currentTrickNumber(), deal.nextPlaceInTrick(), seat, card);
        if (announce != null && deal.playedBy(seat).isEmpty()) {
            List<String> known = Declarations.options(dealt(game, deal, seat), deal.getContract()).stream()
                    .map(Declarations::key)
                    .toList();
            if (!known.contains(announce)) {
                throw new IllegalArgumentException(seat + " cannot announce " + announce + "; may: " + known);
            }
            play.setAnnounced(announce);
        }
        if (Boolean.FALSE.equals(belote) && beloteCards(game, deal, seat).contains(card)) {
            play.setBelote(false);
        }
        deal.add(play);
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
        Map<Team, List<Declaration>> declared = byTeam(declarationsBySeat(game, deal));
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
            belotStatsService.record(game);
            log.info("Belot: table {} goes to {} ({}–{})", game.getId(), winner,
                    game.getNorthSouthScore(), game.getEastWestScore());
        });
    }

    /**
     * The side that took all eight tricks of a played-out hand, or null — for
     * a hand that was not, or was not played out.
     */
    public Team capotBy(BelotDeal deal) {
        if (!deal.isPlayedOut() || deal.getContract() == null) {
            return null;
        }
        return playedDeal(deal, deal.getContract()).capotBy();
    }

    private PlayedDeal playedDeal(BelotDeal deal, Contract contract) {
        List<Trick> tricks = deal.tricks();
        List<Seat> winners = tricks.stream()
                .map(trick -> TrickResolver.winning(trick, contract).orElseThrow().seat())
                .toList();
        return new PlayedDeal(tricks, winners);
    }

    /**
     * What each seat holds that is worth announcing, by seat.
     *
     * <p>Found in the hands rather than asked for. A declaration is in the
     * cards, and the cards are in the seed, so there is nothing for a player
     * to forget to claim and nothing for the server to take their word about.
     *
     * <p>The dealt hand, not what is left of it: a sequence announced on the
     * first trick is still that player’s sequence when the cards have been
     * played away. Less any belote of all trumps that was thrown onto another
     * suit's lead — what the table shows and what the hand scores both come
     * from here, so neither can count one the other has dropped.
     */
    public Map<Seat, List<Declaration>> declarationsBySeat(BelotGame game, BelotDeal deal) {
        Contract contract = deal.getContract();
        List<Trick> tricks = deal.tricks();
        Map<Seat, List<Declaration>> bySeat = new EnumMap<>(Seat.class);
        belotDealService.hands(game, deal).forEach((seat, hand) -> bySeat.put(seat,
                Declarations.standing(Declarations.in(hand, contract, announcedBy(deal, seat)),
                        seat, tricks, contract, belotesKeptQuiet(deal, seat))));
        return bySeat;
    }

    /** What this seat chose to announce with its first card; null if it was not asked. */
    private static String announcedBy(BelotDeal deal, Seat seat) {
        return deal.getPlays().stream()
                .filter(play -> play.getSeat() == seat)
                .findFirst()
                .map(BelotPlay::getAnnounced)
                .orElse(null);
    }

    /** The suits whose belote this seat played without saying. */
    private static Set<Suit> belotesKeptQuiet(BelotDeal deal, Seat seat) {
        return deal.getPlays().stream()
                .filter(play -> play.getSeat() == seat && Boolean.FALSE.equals(play.getBelote()))
                .map(BelotPlay::getSuit)
                .collect(Collectors.toSet());
    }

    /** The eight this seat was dealt, played or not. */
    private List<Card> dealt(BelotGame game, BelotDeal deal, Seat seat) {
        return belotDealService.hands(game, deal).get(seat);
    }

    /**
     * The ways this seat may announce its hand, offered with its first card:
     * the richest first, nothing last. Empty unless it is that seat's turn,
     * its first card of the hand, and it has something to announce.
     */
    public List<List<Declaration>> announcements(BelotGame game, BelotDeal deal, Seat seat) {
        if (legalFor(game, deal, seat).isEmpty() || !deal.playedBy(seat).isEmpty()) {
            return List.of();
        }
        List<List<Declaration>> options = Declarations.options(dealt(game, deal, seat), deal.getContract());
        return options.size() > 1 ? options : List.of();
    }

    /**
     * The cards that would say a belote if this seat played them now: the
     * king and queen of a belote neither of which has been played, when
     * playing one would say it — in all trumps only onto a trick led in its
     * suit, or as the lead. Empty unless it is that seat's turn.
     */
    public List<Card> beloteCards(BelotGame game, BelotDeal deal, Seat seat) {
        List<Card> legal = legalFor(game, deal, seat);
        if (legal.isEmpty()) {
            return List.of();
        }
        Contract contract = deal.getContract();
        List<Card> dealt = dealt(game, deal, seat);
        List<Card> played = deal.playedBy(seat);
        Optional<Suit> led = deal.currentTrick().ledSuit();

        List<Card> cards = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            Card king = new Card(suit, Rank.KING);
            Card queen = new Card(suit, Rank.QUEEN);
            boolean held = contract.isTrump(suit) && dealt.contains(king) && dealt.contains(queen);
            boolean unsaid = !played.contains(king) && !played.contains(queen);
            boolean onItsSuit = contract != Contract.ALL_TRUMPS || led.isEmpty() || led.get() == suit;
            if (held && unsaid && onItsSuit) {
                if (legal.contains(king)) {
                    cards.add(king);
                }
                if (legal.contains(queen)) {
                    cards.add(queen);
                }
            }
        }
        return List.copyOf(cards);
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

    private static Map<Team, List<Declaration>> byTeam(Map<Seat, List<Declaration>> bySeat) {
        Map<Team, List<Declaration>> byTeam = new EnumMap<>(Team.class);
        byTeam.put(Team.NORTH_SOUTH, new ArrayList<>());
        byTeam.put(Team.EAST_WEST, new ArrayList<>());

        bySeat.forEach((seat, declarations) -> byTeam.get(Team.of(seat)).addAll(declarations));
        return byTeam;
    }
}
