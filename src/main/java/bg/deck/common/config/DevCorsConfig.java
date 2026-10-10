package bg.deck.common.config;

import bg.deck.common.constant.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static bg.deck.common.constant.Constants.LOCALHOST;

/**
 * Cross-origin access for local development only.
 *
 * <p>In production the browser never makes a cross-origin request: nginx serves
 * the site and the API from the same origin under {@code /api}, so no CORS
 * headers are needed or wanted. Locally there is no nginx — Vite serves the app
 * on {@code http://localhost:3000} and Spring answers on its own port — which
 * makes every call cross-origin.
 *
 * <p>This bean exists only under the {@code dev} profile, and so does the chain
 * that takes it ({@code SecurityConfig.devSecurityFilterChain}). Production
 * disables CORS outright — exactly the configuration it had before.
 *
 * <p>The allowed origin is the same {@link Constants#LOCALHOST}
 * the WebSocket handshake already permits in dev, so the two cannot disagree.
 */
@Profile("dev")
@Configuration
public class DevCorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(LOCALHOST));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // The client sends its JWT in the Authorization header rather than a
        // cookie, but SockJS opens its transports with credentials.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
