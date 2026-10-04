package bg.deck;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Authentication here is a JWT, read by {@code JwtAuthenticationFilter};
 * nothing ever looks a user up through Spring Security.
 *
 * <p>Left alone, Boot finds no {@code UserDetailsService} bean and invents
 * one: a single in-memory user whose password is generated at every start and
 * printed to the log. It can reach nothing — it holds no role, and no endpoint
 * is open to it — but a fresh credential in the production log on every
 * restart reads as a leak, and an {@code AuthenticationManager} nobody asked
 * for is a thing to explain rather than a thing to have. So it is off.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@EnableAsync
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DeckApplication {

    static void main(String[] args) {
        SpringApplication.run(DeckApplication.class, args);
    }

}
