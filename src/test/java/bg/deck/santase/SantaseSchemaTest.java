package bg.deck.santase;

import bg.deck.common.constant.Constants;
import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.service.UserAccountService;
import bg.deck.santase.enums.Rank;
import bg.deck.santase.enums.Suit;
import bg.deck.santase.model.Card;
import bg.deck.santase.model.SantaseGame;
import bg.deck.santase.model.SantaseGameState;
import bg.deck.santase.model.SantaseSeat;
import bg.deck.santase.repository.SantaseGameRepository;
import bg.deck.santase.service.SantaseSeatService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Santase keeps to its own schema, checked by the database rather than by
 * intention — as belot's is in {@code BelotSchemaTest}.
 *
 * <p>Also the two things the move to that schema changed about how a game is
 * stored: the order of the deck and the hands is a column now, and a seat
 * holds a name rather than a reference to the account.
 */
@DisplayName("Santase keeps to its own schema")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SantaseSeatService.class)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:santaseschema;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class SantaseSchemaTest {

    @Autowired private SantaseGameRepository games;
    @Autowired private SantaseSeatService seats;
    @Autowired private EntityManager entityManager;
    @MockitoBean private UserAccountService userAccountService;

    @Test
    @DisplayName("santase owns exactly its own tables")
    void theTablesLiveInTheirOwnSchema() {
        List<?> tables = entityManager.createNativeQuery("""
                        select lower(table_name) from information_schema.tables
                         where upper(table_schema) = 'SANTASE'
                         order by 1
                        """)
                .getResultList();

        assertThat(tables).isEqualTo(List.of("deck", "game", "game_state", "hand", "player_stats", "seat"));
    }

    @Test
    @DisplayName("nothing santase owns points out of the schema")
    void noForeignKeyLeavesTheSchema() {
        List<?> references = entityManager.createNativeQuery("""
                        select fk.constraint_name
                          from information_schema.referential_constraints fk
                         where upper(fk.constraint_schema) = 'SANTASE'
                           and upper(fk.unique_constraint_schema) <> 'SANTASE'
                        """)
                .getResultList();

        assertThat(references).isEmpty();
    }

    @Test
    @DisplayName("the deck and a hand come back in the order they were left in, after cards are taken and added")
    void orderSurvivesARoundTrip() {
        SantaseGame game = newGame("petko91", "ninja2011");
        UUID gameId = games.save(game).getId();
        flushAndClear();

        // What a trick does: the next card leaves the top of the deck and goes
        // to a hand; a card leaves the hand.
        SantaseGame saved = games.findById(gameId).orElseThrow();
        List<Card> deck = saved.getState().getDeck();
        List<UUID> deckLeft = ids(deck.subList(1, deck.size()));
        Card drawn = deck.removeFirst();
        SantaseSeat first = saved.getFirstPlayer();
        first.getHand().removeFirst();
        first.drawCard(drawn);
        List<UUID> handLeft = ids(first.getHand());
        flushAndClear();

        SantaseGame reread = games.findById(gameId).orElseThrow();
        assertThat(ids(reread.getState().getDeck())).containsExactlyElementsOf(deckLeft);
        assertThat(ids(reread.getFirstPlayer().getHand())).containsExactlyElementsOf(handLeft);
        assertThat(reread.getFirstPlayer().getHand().getLast().getId()).isEqualTo(drawn.getId());
        assertThat(reread.getFirstPlayer().getHand().getLast().getRank()).isEqualTo(drawn.getRank());
    }

    @Test
    @DisplayName("a deleted account's seats keep their games, under the tombstone name")
    void aDeletedAccountsSeatsAreRenamed() {
        UUID gameId = games.save(newGame("petko91", "ninja2011")).getId();
        flushAndClear();

        assertThat(seats.anonymise("petko91")).isEqualTo(1);
        flushAndClear();

        SantaseGame reread = games.findById(gameId).orElseThrow();
        assertThat(reread.getFirstPlayer().getUsername()).isEqualTo(Constants.DELETED_PLAYER);
        assertThat(reread.getSecondPlayer().getUsername()).isEqualTo("ninja2011");
        assertThat(games.findActiveGamesByUsername("petko91")).isEmpty();
    }

    @Test
    @DisplayName("a seat is made only for an account that exists")
    void aSeatNeedsAnAccount() {
        when(userAccountService.existsByUsername("petko91")).thenReturn(true);

        assertThat(seats.newSeatFor("petko91").getUsername()).isEqualTo("petko91");
        assertThatThrownBy(() -> seats.newSeatFor("nobody")).isInstanceOf(InvalidCredentialsException.class);
    }

    private SantaseGame newGame(String first, String second) {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(Card.builder().id(UUID.randomUUID()).suit(suit).rank(rank)
                        .isPlayable(true).isLastDrawn(false).build());
            }
        }
        SantaseSeat one = seat(first, new ArrayList<>(deck.subList(0, 6)));
        SantaseSeat two = seat(second, new ArrayList<>(deck.subList(6, 12)));
        entityManager.persist(one);
        entityManager.persist(two);
        SantaseGameState state = SantaseGameState.builder()
                .deck(new ArrayList<>(deck.subList(12, deck.size())))
                .trumpCard(deck.getLast())
                .build();
        state.setFirstTurnPlayer(one);
        state.setInTurnPlayer(one);
        return SantaseGame.builder().firstPlayer(one).secondPlayer(two).state(state).build();
    }

    private static SantaseSeat seat(String username, List<Card> hand) {
        return SantaseSeat.builder().username(username).hand(hand).result(0).score(0)
                .isBlanked(true).inactivityCount(0).build();
    }

    private static List<UUID> ids(List<Card> cards) {
        return cards.stream().map(Card::getId).toList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
