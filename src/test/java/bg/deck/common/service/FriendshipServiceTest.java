package bg.deck.common.service;

import bg.deck.common.enums.FriendshipStatus;
import bg.deck.common.enums.Role;
import bg.deck.common.model.Friendship;
import bg.deck.common.repository.FriendshipRepository;
import bg.deck.common.exception.BadRequestException;
import bg.deck.common.exception.NotFoundException;
import bg.deck.common.model.User;
import bg.deck.common.model.dto.FriendLinks;
import bg.deck.common.model.response.FriendSearchHit;
import bg.deck.common.model.response.FriendSearchResponse;
import bg.deck.common.enums.FriendRelation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Friendships against a real database: asking, answering, ending, and what
 * each player's list says afterwards. What is told over the socket is sent
 * after the commit, which a test transaction never reaches; the rows are the
 * point here.
 */
@DisplayName("Friendships")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FriendshipService.class, UserAccountService.class})
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:friendships;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class FriendshipServiceTest {

    @Autowired private FriendshipService friendships;
    @Autowired private UserAccountService accounts;
    @Autowired private EntityManager entityManager;
    @Autowired private FriendshipRepository rows;
    @MockitoBean private WebSocketService webSocketService;

    @BeforeEach
    void accounts() {
        for (String name : List.of("petko91", "gosho", "goshev", "ivan",
                "player1", "player10", "player11", "player12", "player2")) {
            User user = new User();
            user.setUsername(name);
            user.setEmail(name + "@example.com");
            user.setPassword("x");
            user.setRole(Role.ROLE_USER);
            user.setIsEmailConfirmed(true);
            accounts.save(user);
        }
        flush();
    }

    @Test
    @DisplayName("a request waits on both lists until it is accepted, then both are friends")
    void askAndAccept() {
        friendships.request("petko91", "gosho");
        flush();

        assertThat(friendships.linksOf("petko91").outgoing()).extracting("username").containsExactly("gosho");
        FriendLinks gosho = friendships.linksOf("gosho");
        assertThat(gosho.incoming()).extracting("username").containsExactly("petko91");
        assertThat(friendships.areFriends("petko91", "gosho")).isFalse();

        friendships.accept("gosho", gosho.incoming().getFirst().id());
        flush();

        assertThat(friendships.linksOf("petko91").friends()).containsExactly("gosho");
        assertThat(friendships.linksOf("gosho").friends()).containsExactly("petko91");
        assertThat(friendships.areFriends("gosho", "petko91")).isTrue();
    }

    @Test
    @DisplayName("asking someone who has already asked you accepts their request")
    void askingBackAccepts() {
        friendships.request("petko91", "gosho");
        friendships.request("gosho", "petko91");
        flush();

        assertThat(friendships.areFriends("petko91", "gosho")).isTrue();
        assertThat(friendships.linksOf("gosho").incoming()).isEmpty();
    }

    @Test
    @DisplayName("a name typed in another case finds the player")
    void anyCase() {
        friendships.request("petko91", "GOSHO");
        flush();

        assertThat(friendships.linksOf("gosho").incoming()).extracting("username").containsExactly("petko91");
    }

    @Test
    @DisplayName("refuses yourself, nobody, a second request and an existing friend")
    void refusals() {
        assertThrows(BadRequestException.class, () -> friendships.request("petko91", "petko91"));
        assertThrows(NotFoundException.class, () -> friendships.request("petko91", "nobody"));

        friendships.request("petko91", "gosho");
        flush();
        assertThrows(BadRequestException.class, () -> friendships.request("petko91", "gosho"));

        friendships.accept("gosho", friendships.linksOf("gosho").incoming().getFirst().id());
        flush();
        assertThrows(BadRequestException.class, () -> friendships.request("petko91", "gosho"));
    }

    @Test
    @DisplayName("only the player asked can accept; a declined request leaves both lists and is kept as REJECTED")
    void declining() {
        friendships.request("petko91", "gosho");
        flush();
        UUID id = friendships.linksOf("gosho").incoming().getFirst().id();

        assertThrows(NotFoundException.class, () -> friendships.accept("petko91", id));
        assertThrows(NotFoundException.class, () -> friendships.decline("ivan", id));

        friendships.decline("gosho", id);
        flush();

        assertThat(friendships.linksOf("petko91").outgoing()).isEmpty();
        assertThat(friendships.linksOf("gosho").incoming()).isEmpty();
        assertThat(rows.findById(id).map(Friendship::getStatus)).contains(FriendshipStatus.REJECTED);
    }

    @Test
    @DisplayName("a request taken back is kept as CANCELLED, and asking again reuses the row")
    void cancellingAndAskingAgain() {
        friendships.request("petko91", "gosho");
        flush();
        UUID id = friendships.linksOf("petko91").outgoing().getFirst().id();

        friendships.decline("petko91", id);
        flush();
        assertThat(rows.findById(id).map(Friendship::getStatus)).contains(FriendshipStatus.CANCELLED);

        friendships.request("gosho", "petko91");
        flush();
        assertThat(rows.count()).isEqualTo(1);
        assertThat(friendships.linksOf("petko91").incoming()).extracting("username").containsExactly("gosho");
        assertThat(friendships.search("petko91", "gos").players())
                .containsExactly(hit("goshev", FriendRelation.NONE), hit("gosho", FriendRelation.RECEIVED));
    }

    @Test
    @DisplayName("either friend can end it, and only a friendship can be ended")
    void removing() {
        friendships.request("petko91", "gosho");
        friendships.request("gosho", "petko91");
        flush();

        assertThrows(NotFoundException.class, () -> friendships.remove("petko91", "ivan"));
        friendships.remove("gosho", "petko91");
        flush();

        assertThat(friendships.areFriends("petko91", "gosho")).isFalse();
        assertThat(friendships.linksOf("petko91").friends()).isEmpty();
        assertThat(rows.findAll()).extracting(Friendship::getStatus).containsExactly(FriendshipStatus.REMOVED);

        friendships.request("petko91", "gosho");
        flush();
        assertThat(rows.count()).isEqualTo(1);
        assertThat(friendships.linksOf("gosho").incoming()).extracting("username").containsExactly("petko91");
    }

    @Test
    @DisplayName("search finds names by their start, in any case, with a request either way said; never you nor a friend")
    void searching() {
        assertThat(friendships.search("petko91", "go").players()).isEmpty();
        assertThat(friendships.search("petko91", "  GOS ").players())
                .containsExactly(hit("goshev", FriendRelation.NONE), hit("gosho", FriendRelation.NONE));

        friendships.request("petko91", "gosho");
        flush();
        assertThat(friendships.search("petko91", "gos").players())
                .containsExactly(hit("goshev", FriendRelation.NONE), hit("gosho", FriendRelation.SENT));
        assertThat(friendships.search("gosho", "pet").players()).containsExactly(hit("petko91", FriendRelation.RECEIVED));
        assertThat(friendships.search("petko91", "pet").players()).isEmpty();

        friendships.request("gosho", "petko91");
        flush();
        assertThat(friendships.search("petko91", "gos").players()).containsExactly(hit("goshev", FriendRelation.NONE));
    }

    @Test
    @DisplayName("a player turned down waits a week to ask again; the one who said no can ask at once")
    void askingAgainAfterRejection() {
        friendships.request("petko91", "gosho");
        flush();
        friendships.decline("gosho", friendships.linksOf("gosho").incoming().getFirst().id());
        flush();

        BadRequestException tooSoon = assertThrows(BadRequestException.class,
                () -> friendships.request("petko91", "gosho"));
        assertThat(tooSoon.getMessage()).contains("gosho").contains("7");

        friendships.request("gosho", "petko91");
        flush();
        assertThat(friendships.linksOf("petko91").incoming()).extracting("username").containsExactly("gosho");
    }

    @Test
    @DisplayName("a week after being turned down, asking again works")
    void askingAgainAWeekLater() {
        friendships.request("petko91", "gosho");
        flush();
        friendships.decline("gosho", friendships.linksOf("gosho").incoming().getFirst().id());
        flush();
        Friendship row = rows.findAll().getFirst();
        row.setRespondedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(8)));
        rows.save(row);
        flush();

        friendships.request("petko91", "gosho");
        flush();
        assertThat(friendships.linksOf("gosho").incoming()).extracting("username").containsExactly("petko91");
    }

    private static FriendSearchHit hit(String username, FriendRelation relation) {
        return new FriendSearchHit(username, relation);
    }

    @Test
    @DisplayName("more than fit: no list but the name typed in full, and a word to type more")
    void tooManyToShow() {
        FriendSearchResponse many = friendships.search("petko91", "play");
        assertThat(many.more()).isTrue();
        assertThat(many.players()).isEmpty();

        FriendSearchResponse exact = friendships.search("petko91", "Player1");
        assertThat(exact.more()).isTrue();
        assertThat(exact.players()).containsExactly(hit("player1", FriendRelation.NONE));

        assertThat(friendships.search("petko91", "player11").players())
                .containsExactly(hit("player11", FriendRelation.NONE));
        assertThat(friendships.search("petko91", "player11").more()).isFalse();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
