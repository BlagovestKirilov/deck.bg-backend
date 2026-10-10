package bg.deck.common.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import static bg.deck.common.constant.Constants.USER;
import static bg.deck.common.constant.ExceptionConstants.INVALID_TOKEN;

/**
 * The filter chain, in two variants that differ in exactly one respect:
 * whether cross-origin requests are allowed.
 *
 * <p>Both are written here rather than in separate classes so the one
 * difference is visible side by side. Everything they share lives in
 * {@link #shared(HttpSecurity)} and is written once — a copy that drifts is a
 * hole in one environment that does not show up in the other.
 */
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Local development: CORS allowed.
     *
     * <p>Vite serves the app on its own port while Spring answers on another,
     * so every call the browser makes is cross-origin. The source is taken as a
     * method parameter rather than a field because it is only resolved when
     * this bean is created — under any other profile the bean does not exist
     * and is never asked for.
     */
    @Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http,
                                                      CorsConfigurationSource corsConfigurationSource) {
        return shared(http)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .build();
    }

    /**
     * Every deployed environment: no CORS.
     *
     * <p>nginx serves the site and proxies the API under the same origin, so
     * the browser never makes a cross-origin request and there is nothing to
     * allow. The disable is not redundant: Spring Security applies
     * {@code cors(withDefaults())} by itself whenever a
     * {@code UrlBasedCorsConfigurationSource} bean is present in the context
     * (see {@code HttpSecurityConfiguration.applyCorsIfAvailable}). Without
     * this line, one such bean declared without a {@code dev} profile would
     * silently switch CORS on in production.
     *
     * <p>Bound to {@code !dev} rather than to {@code prod} on purpose. Were it
     * named after one profile, running under any third profile would match
     * neither bean, leaving the application with no filter chain at all — which
     * fails open, with every endpoint unauthenticated. This way anything that
     * is not development gets the locked-down chain.
     */
    @Bean
    @Profile("!dev")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) {
        return shared(http)
                .cors(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Stateless JWT auth and the endpoint rules, identical in every profile.
     *
     * <p>CSRF is off because there is nothing for it to protect: the only
     * credential is a JWT read from the {@code Authorization} header, no cookie
     * or session is ever issued, and a browser does not attach that header to a
     * request another site triggers. <b>That stops being true the moment any
     * credential moves into a cookie</b> — if a refresh token is ever stored
     * that way, CSRF protection has to come back, or {@code SameSite=Strict}
     * has to take its place.
     */
    private HttpSecurity shared(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        // Liveness for the deploy, and nothing else: only
                        // health is exposed, and it answers UP or DOWN without
                        // saying anything about why.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/santase/**").hasRole(USER)
                        .requestMatchers("/tabla/**").hasRole(USER)
                        .requestMatchers("/belot/**").hasRole(USER)
                        .requestMatchers("/user/confirm-deletion").permitAll()
                        .requestMatchers("/user/**").hasRole(USER)
                        // The socket's handshake is open; the socket is not. A
                        // browser cannot put a header on a WebSocket handshake,
                        // which is why the token used to ride in the URL and
                        // land in every access log. Authentication happens one
                        // frame later, on STOMP CONNECT, in
                        // StompAuthChannelInterceptor: without a valid token
                        // the connection is refused, and a connected player can
                        // only subscribe to their own topics. Opening the
                        // handshake buys an anonymous caller nothing but a
                        // socket that refuses to talk.
                        .requestMatchers("/ws-game/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }

    /**
     * The answer to a request that needed authentication and did not have it:
     * 401, the same status and body the JWT filter used to write itself.
     *
     * <p>Spring's own default here is 403, which the client does not act on —
     * it renews the session on a 401 and on nothing else. Stated explicitly,
     * an expired token still ends in a renewed session and the request going
     * through, exactly as before, while a request that needs no token is no
     * longer refused over the token it happens to carry.
     */
    private static AuthenticationEntryPoint unauthorized() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(INVALID_TOKEN);
        };
    }
}
