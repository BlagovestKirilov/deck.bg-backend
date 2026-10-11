package bg.deck.common;

import bg.deck.common.enums.Role;
import bg.deck.common.enums.Scope;
import bg.deck.common.enums.ServiceState;
import bg.deck.common.model.AvailableService;
import bg.deck.common.model.User;
import bg.deck.common.repository.AvailableServiceRepository;
import bg.deck.common.service.CacheService;
import bg.deck.common.service.JwtService;
import bg.deck.common.service.UserAccountService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /admin, through the real security chain.
 *
 * <p>Who is an admin is the database's answer, read on every request: a token
 * says what the role was when it was minted, and that is not good enough for
 * the one part of the site that changes what everybody else sees.
 */
@DisplayName("The admin page's API")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:adminaccess;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class AdminAccessTest {

    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter security;
    @Autowired private UserAccountService accounts;
    @Autowired private AvailableServiceRepository catalogue;
    @Autowired private CacheService cacheService;
    @Autowired private JwtService jwt;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        accounts.findByUsername("boss").ifPresent(accounts::delete);
        accounts.findByUsername("petko91").ifPresent(accounts::delete);
        catalogue.deleteAll();
        // Made one after another, so "newest first" has an order to show.
        account("boss", "boss@deck.bg", Role.ROLE_ADMIN);
        account("petko91", "petko@deck.bg", Role.ROLE_USER);
        game("SANTASE", ServiceState.ON, Scope.PUBLIC);
        game("BELOT", ServiceState.OFF, Scope.BETA);
    }

    @Test
    @DisplayName("is refused without a token, and to a player who is not an admin")
    void refusedToStrangersAndPlayers() throws Exception {
        mvc.perform(get("/admin/services")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/services").header("Authorization", bearer("petko91")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("is open to an admin, who can still play")
    void openToAnAdmin() throws Exception {
        mvc.perform(get("/admin/services").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BELOT"))
                .andExpect(jsonPath("$[0].state").value("OFF"))
                .andExpect(jsonPath("$[0].requiredScope").value("BETA"));
        mvc.perform(get("/santase/active").header("Authorization", bearer("boss")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("follows the database, not the token: a promotion and a demotion count at once")
    void theDatabaseDecides() throws Exception {
        String playersToken = bearer("petko91");
        String adminsToken = bearer("boss");

        setRole("petko91", Role.ROLE_ADMIN);
        setRole("boss", Role.ROLE_USER);

        mvc.perform(get("/admin/services").header("Authorization", playersToken)).andExpect(status().isOk());
        mvc.perform(get("/admin/services").header("Authorization", adminsToken)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("switches a game, and the catalogue everybody reads changes at once")
    void aSwitchIsSeenAtOnce() throws Exception {
        // Read once, so the catalogue is sitting in the cache.
        assertThat(cacheService.availableServices().find("BELOT").orElseThrow().state()).isEqualTo(ServiceState.OFF);

        mvc.perform(put("/admin/services/BELOT").header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"ON\",\"requiredScope\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ON"));

        assertThat(cacheService.availableServices().find("BELOT").orElseThrow().state()).isEqualTo(ServiceState.ON);
    }

    @Test
    @DisplayName("answers 404 for a game that is not there, and 400 for a switch with no state")
    void badSwitches() throws Exception {
        mvc.perform(put("/admin/services/POKER").header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"ON\",\"requiredScope\":\"PUBLIC\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/admin/services/BELOT").header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requiredScope\":\"PUBLIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lists players a page at a time, newest first unless asked otherwise")
    void pagesOfPlayers() throws Exception {
        mvc.perform(get("/admin/players").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].username").value("petko91"))
                .andExpect(jsonPath("$.players[1].username").value("boss"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(8))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));

        mvc.perform(get("/admin/players").param("size", "1").param("page", "1")
                        .header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].username").value("boss"))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get("/admin/players").param("sort", "username,asc").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.players[0].username").value("boss"));

        mvc.perform(get("/admin/players").param("size", "500").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("finds players by part of the username, in any case, and by role and access")
    void filtersPlayers() throws Exception {
        mvc.perform(get("/admin/players").param("username", "KO9").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.players[0].username").value("petko91"))
                .andExpect(jsonPath("$.players[0].role").value("USER"));
        // The email is not searched: "deck" is in both emails and in neither name.
        mvc.perform(get("/admin/players").param("username", "deck").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/admin/players").param("username", "%").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.total").value(0));

        mvc.perform(get("/admin/players").param("role", "ADMIN").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.players[0].username").value("boss"));
        mvc.perform(get("/admin/players").param("scope", "BETA").header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/admin/players").param("role", "USER").param("scope", "PUBLIC")
                        .header("Authorization", bearer("boss")))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("refuses a sort by an unknown field, and a role that is not one")
    void refusesWhatItCannotRead() throws Exception {
        mvc.perform(get("/admin/players").param("sort", "password,asc").header("Authorization", bearer("boss")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/players").param("role", "ROOT").header("Authorization", bearer("boss")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("looks a player up across every game, and sets their scope")
    void looksUpAndSetsScope() throws Exception {
        mvc.perform(get("/admin/players/petko91").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.scope").value("PUBLIC"))
                .andExpect(jsonPath("$.games[0].code").value("SANTASE"))
                .andExpect(jsonPath("$.games[1].code").value("TABLA"))
                .andExpect(jsonPath("$.games[2].code").value("BELOT"))
                .andExpect(jsonPath("$.games[0].record.rating").value(1500))
                .andExpect(jsonPath("$.games[0].recent.length()").value(0));

        mvc.perform(put("/admin/players/petko91/scope").header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"BETA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("BETA"));
        assertThat(accounts.findByUsername("petko91").orElseThrow().getScope()).isEqualTo(Scope.BETA);

        mvc.perform(get("/admin/players/nobody").header("Authorization", bearer("boss")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tells the profile who is an admin")
    void theProfileSaysWhoIsAnAdmin() throws Exception {
        mvc.perform(get("/user/profile").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/user/profile").header("Authorization", bearer("petko91")))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    private void account(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("not-a-real-hash");
        user.setRole(role);
        user.setIsEmailConfirmed(true);
        accounts.save(user);
        // Apart in time, so the newest-first order cannot depend on a tie.
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void game(String code, ServiceState state, Scope scope) {
        AvailableService service = new AvailableService();
        service.setCode(code);
        service.setState(state);
        service.setRequiredScope(scope);
        catalogue.save(service);
    }

    private void setRole(String username, Role role) {
        User user = accounts.findByUsername(username).orElseThrow();
        user.setRole(role);
        accounts.save(user);
    }

    /** A token minted now, carrying whatever role the account has at this moment. */
    private String bearer(String username) {
        return "Bearer " + jwt.generateToken(accounts.findByUsername(username).orElseThrow());
    }
}
