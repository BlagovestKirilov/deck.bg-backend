package bg.deck.common.security;

import bg.deck.common.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the JWT filter does with the token it is given — and, just as much,
 * what it does not do to the request that carries it.
 */
@DisplayName("Reading the token on a request")
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("an expired token leaves the request unauthenticated, not refused")
    void expiredTokenDoesNotRefuseTheRequest() throws Exception {
        // The case behind the bug: a browser whose session ran out opens a
        // password-reset link. The endpoint needs no token; the stale one it
        // sends anyway must not answer for it.
        when(jwtService.extractUsername(any())).thenThrow(new ExpiredJwtException(null, null, "expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/forgot-password/verify");
        request.addHeader("Authorization", "Bearer expired.jwt.here");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), "the filter wrote no answer of its own");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "and authenticated nobody");
    }

    @Test
    @DisplayName("a token that is not a token at all is treated the same way")
    void garbageTokenIsIgnored() throws Exception {
        when(jwtService.extractUsername(any())).thenThrow(new IllegalArgumentException("not a jwt"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/forgot-password/verify");
        request.addHeader("Authorization", "Bearer nonsense");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("a valid token authenticates the caller, with their role")
    void validTokenAuthenticates() throws Exception {
        when(jwtService.extractUsername("good.jwt")).thenReturn("petko91");
        when(jwtService.extractRole("good.jwt")).thenReturn("ROLE_USER");
        when(jwtService.isTokenValid("good.jwt")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/santase/state");
        request.addHeader("Authorization", "Bearer good.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("petko91", authentication.getName());
        assertEquals(List.of("ROLE_USER"), authentication.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test
    @DisplayName("no token at all: straight through, and nobody is authenticated")
    void noTokenPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
