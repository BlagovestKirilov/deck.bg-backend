package bg.deck;

import bg.deck.belot.engine.BidKind;
import bg.deck.belot.engine.Card;
import bg.deck.belot.engine.Contract;
import bg.deck.belot.engine.DealResult;
import bg.deck.belot.engine.Doubling;
import bg.deck.belot.engine.Rank;
import bg.deck.belot.engine.Seat;
import bg.deck.belot.engine.Suit;
import bg.deck.belot.engine.Team;
import bg.deck.belot.model.BelotDealStatus;
import bg.deck.belot.model.BelotGameStatus;
import bg.deck.belot.model.request.BelotBidRequest;
import bg.deck.belot.model.request.BelotPlayRequest;
import bg.deck.belot.model.response.BelotBidView;
import bg.deck.belot.engine.DeclarationKind;
import bg.deck.belot.model.response.BelotDealRow;
import bg.deck.belot.model.response.BelotDeclarationView;
import bg.deck.belot.model.response.BelotDeclarationsView;
import bg.deck.belot.model.response.BelotBiddingView;
import bg.deck.belot.model.response.BelotPlayView;
import bg.deck.belot.model.response.BelotProfileResponse;
import bg.deck.belot.model.response.BelotPlayedCard;
import bg.deck.belot.model.response.BelotSeatView;
import bg.deck.belot.model.response.BelotTurnView;
import bg.deck.belot.model.response.BelotStateResponse;
import bg.deck.model.dto.CardDTO;
import bg.deck.model.request.CardRequest;
import bg.deck.model.request.ChangeForgottenPasswordRequest;
import bg.deck.model.request.ChangePasswordRequest;
import bg.deck.model.request.ConfirmDeletionRequest;
import bg.deck.model.request.ForgotPasswordEmailRequest;
import bg.deck.model.request.LoginRequest;
import bg.deck.model.request.MoveRequest;
import bg.deck.model.request.RefreshRequest;
import bg.deck.model.request.RegisterRequest;
import bg.deck.model.request.UserDeletionRequest;
import bg.deck.model.response.AuthResponse;
import bg.deck.model.response.AvailableServicesResponse;
import bg.deck.model.dto.ComboHopDTO;
import bg.deck.model.response.ErrorResponse;
import bg.deck.model.response.GameStateResponse;
import bg.deck.model.dto.GameStatsDTO;
import bg.deck.model.dto.HopDTO;
import bg.deck.model.dto.OpeningThrowDTO;
import bg.deck.model.response.ProfileResponse;
import bg.deck.model.response.SearchGameResponse;
import bg.deck.model.response.TablaStateResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JSON the server sends and reads, pinned.
 *
 * <p>The client mirrors these shapes by hand, with no codegen, so any change
 * to a key — a renamed field, a dropped one, a boolean whose "is" prefix
 * Jackson decides to strip — breaks it silently. Every response is written
 * with every field set, and every request is read from the JSON the client
 * sends and validated empty; the result must match the snapshot committed
 * alongside this test. Refactoring a model class (to a record, say) is only
 * safe when this still passes.
 *
 * <p>To record a new snapshot after a deliberate change to the wire format:
 * {@code SNAPSHOT_WRITE=true ./mvnw test -Dtest=WireFormatSnapshotTest}.
 */
@DisplayName("The wire format")
class WireFormatSnapshotTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/wire-format-snapshot.json");

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final JsonMapper json = JsonMapper.builder().build();

    private final Validator validator = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    @Test
    @DisplayName("is exactly what it was")
    void matchesTheSnapshot() throws Exception {
        ObjectNode now = json.createObjectNode();
        responses().forEach((name, value) -> now.set(name, json.valueToTree(value)));
        requests().forEach((name, value) -> now.set(name, value));

        String written = json.writerWithDefaultPrettyPrinter().writeValueAsString(now);
        if ("true".equals(System.getenv("SNAPSHOT_WRITE"))) {
            Files.writeString(SNAPSHOT, written + System.lineSeparator(), StandardCharsets.UTF_8);
            return;
        }

        JsonNode expected = json.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        // Compared as trees: key order is not part of the contract, keys are.
        assertEquals(expected, json.readTree(written),
                "the JSON on the wire changed — if that was meant, re-record the snapshot");
    }

    /* ---------------- what the server sends ---------------- */

    private Map<String, Object> responses() {
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("AuthResponse", AuthResponse.builder()
                .status("SUCCESS").message("ok").token("access").refreshToken("refresh").build());

        out.put("ErrorResponse", ErrorResponse.builder()
                .timestamp(Instant.parse("2026-09-22T10:15:30Z")).status(400).error("Bad Request")
                .message("Невалиден ход.").details(Map.of("field", "reason")).path("/tabla/move").build());

        out.put("AvailableServicesResponse",
                new AvailableServicesResponse(List.of("SANTASE", "TABLA")));

        // Belot sends one of these per seat, each with that seat’s own hand.
        out.put("BelotProfileResponse", new BelotProfileResponse(31, 18, 13, "GOLD", 0));

        out.put("BelotStateResponse", new BelotStateResponse(
                ID,
                BelotGameStatus.PLAYING,
                null,
                "0f5c1b6c9b4b4d2f8a1e6d3c2b7a9e8f0a1b2c3d4e5f60718293a4b5c6d7e8f9",
                List.of(new BelotSeatView(Seat.NORTH, Team.NORTH_SOUTH, "petko91", 5),
                        new BelotSeatView(Seat.WEST, Team.EAST_WEST, "ninja2011", 5),
                        new BelotSeatView(Seat.SOUTH, Team.NORTH_SOUTH, "gosho", 5),
                        new BelotSeatView(Seat.EAST, Team.EAST_WEST, "ivan", 5)),
                Seat.NORTH,
                3,
                Seat.WEST,
                BelotDealStatus.BIDDING,
                List.of(new Card(Suit.SPADES, Rank.ACE), new Card(Suit.HEARTS, Rank.JACK)),
                new BelotBiddingView(
                        Seat.SOUTH,
                        Contract.HEARTS,
                        Seat.NORTH,
                        Doubling.CONTRA,
                        List.of(new BelotBidView(Seat.NORTH, BidKind.BID, Contract.HEARTS),
                                new BelotBidView(Seat.WEST, BidKind.CONTRA, null)),
                        List.of(new BelotBidView(Seat.SOUTH, BidKind.PASS, null))),
                new BelotPlayView(
                        Contract.HEARTS,
                        Seat.NORTH,
                        Seat.EAST,
                        4,
                        List.of(new BelotPlayedCard(Seat.NORTH, new Card(Suit.CLUBS, Rank.TEN)),
                                new BelotPlayedCard(Seat.WEST, new Card(Suit.CLUBS, Rank.KING))),
                        null,
                        List.of(new Card(Suit.CLUBS, Rank.SEVEN))),
                new BelotTurnView(Seat.EAST, Instant.parse("2026-09-27T10:15:30Z")),
                new BelotDeclarationsView(
                        List.of(new BelotDeclarationView(Seat.NORTH, DeclarationKind.TERZ,
                                        Suit.SPADES, Rank.KING, 20),
                                new BelotDeclarationView(Seat.EAST, DeclarationKind.BELOTE,
                                        Suit.HEARTS, Rank.KING, 20)),
                        20, 20),
                List.of(new BelotDealRow(1, Contract.SPADES, Seat.NORTH, Team.NORTH_SOUTH,
                        Doubling.NONE, 97, 65, 10, 6, DealResult.MADE),
                        new BelotDealRow(2, Contract.NO_TRUMPS, Seat.EAST, Team.EAST_WEST,
                                Doubling.CONTRA, 120, 140, 0, 52, DealResult.INSIDE)),
                91, 64, 0));

        out.put("SearchGameResponse.waiting", SearchGameResponse.waiting());
        out.put("SearchGameResponse.started", SearchGameResponse.started(ID));

        CardDTO card = CardDTO.builder()
                .id(ID).suit("HEARTS").rank("ACE").points(11).isPlayable(true).isLastDrawn(true).build();
        out.put("CardDTO", card);

        out.put("GameStateResponse", GameStateResponse.builder()
                .gameId("g1").deck(List.of(card)).trumpCard(card).playedCard(card).opponentPlayedCard(card)
                .opponentPlayerCardsCount(6).remainingCardsCount(12)
                .firstPlayerUsername("petko91").firstPlayerResult(3)
                .secondPlayerUsername("ninja2011").secondPlayerResult(2)
                .isOnTurn(true).isClosed(true)
                .winnerUsername("petko91").trickWinnerUsername("petko91").surrenderPlayerUsername("ninja2011")
                .trickFirstPlayerScore(66).trickSecondPlayerScore(40)
                .bonus(20).opponentPlayerBonus(40).inactivityCount(1).nextMoveTimeInSeconds(18)
                .build());

        HopDTO hop = new HopDTO(24, 18, 6, true, false, false);
        out.put("HopDTO", hop);
        ComboHopDTO combo = new ComboHopDTO(24, 13, List.of(18), List.of(6, 5));
        out.put("ComboHopDTO", combo);
        OpeningThrowDTO opening = new OpeningThrowDTO(5, 2);
        out.put("OpeningThrowDTO", opening);

        out.put("TablaStateResponse", TablaStateResponse.builder()
                .gameId("g1").gameType("TABLA").firstPlayerUsername("petko91").secondPlayerUsername("ninja2011")
                .mySide("WHITE").points(List.of(2, 0, -5)).myBar(1).opponentBar(2).myOff(3).opponentOff(4)
                .myPipCount(167).opponentPipCount(160).isOnTurn(true)
                .die1(5).die2(2).remainingDice(List.of(5, 2)).maxDiceUsable(2).usedDiceCount(0)
                .mustConfirm(true).noMovesAvailable(true)
                .legalHops(List.of(hop)).comboHops(List.of(combo)).pendingHops(List.of(hop))
                .winnerUsername("petko91").surrenderPlayerUsername("ninja2011").resultKind("GAMMON")
                .inactivityCount(1).nextMoveTimeInSeconds(40)
                .openingPhase(true).openingThrows(List.of(opening)).openingMine(5).openingOpponent(2)
                .serverSeedHash("abc").serverSeed("def")
                .build());

        GameStatsDTO stats = new GameStatsDTO(10, 4, "GOLD", 0);
        out.put("GameStatsDTO", stats);
        out.put("ProfileResponse", profile(stats));

        return out;
    }

    private static ProfileResponse profile(GameStatsDTO stats) {
        return ProfileResponse.builder()
                .santaseWins(10)
                .santaseLosses(4)
                .rank("GOLD")
                .emailConfirmed(true)
                .stats(Map.of("SANTASE", stats))
                .build();
    }

    /* ---------------- what the server reads ---------------- */

    /**
     * Each request as the client sends it, read and written back — which shows
     * the fields the server actually takes in — and the constraints an empty
     * body breaks, which shows the validation is still attached.
     */
    private Map<String, JsonNode> requests() {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        read(out, CardRequest.class, "{\"cardId\":\"" + ID + "\"}");
        read(out, ChangeForgottenPasswordRequest.class, "{\"newPassword\":\"secret12\",\"token\":\"" + ID + "\"}");
        read(out, ChangePasswordRequest.class, "{\"currentPassword\":\"secret12\",\"newPassword\":\"secret34\"}");
        read(out, ConfirmDeletionRequest.class, "{\"token\":\"" + ID + "\"}");
        read(out, ForgotPasswordEmailRequest.class, "{\"email\":\"petko@example.com\"}");
        read(out, LoginRequest.class, "{\"username\":\"petko91\",\"password\":\"secret12\"}");
        read(out, MoveRequest.class, "{\"from\":24,\"die\":6}");
        read(out, RefreshRequest.class, "{\"refreshToken\":\"refresh\"}");
        read(out, RegisterRequest.class, "{\"username\":\"petko91\",\"password\":\"secret12\",\"email\":\"petko@example.com\"}");
        read(out, UserDeletionRequest.class, "{\"password\":\"secret12\"}");
        read(out, BelotBidRequest.class, "{\"kind\":\"BID\",\"contract\":\"ALL_TRUMPS\"}");
        read(out, BelotPlayRequest.class, "{\"card\":{\"suit\":\"SPADES\",\"rank\":\"ACE\"}}");

        return out;
    }

    private void read(Map<String, JsonNode> out, Class<?> type, String body) {
        ObjectNode node = json.createObjectNode();
        Object full = json.readValue(body, type);
        node.set("readsBack", json.valueToTree(full));
        node.put("violationsWhenFull", violations(full).toString());
        node.put("violationsWhenEmpty", violations(json.readValue("{}", type)).toString());
        out.put(type.getSimpleName(), node);
    }

    private Set<String> violations(Object value) {
        Set<String> out = new TreeSet<>();
        for (ConstraintViolation<Object> v : validator.validate(value)) {
            out.add(v.getPropertyPath() + ":" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        }
        return out;
    }
}
