package bg.deck.common.config;

import bg.deck.common.constant.LogConstants;
import bg.deck.santase.service.SantaseService;
import bg.deck.tabla.service.TablaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Log4j2
@RequiredArgsConstructor
@Component
public class WebSocketEventListener {

    private final SantaseService santaseService;
    private final TablaService tablaService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (event.getUser() == null) {
            log.warn(LogConstants.WS_DISCONNECT_NO_AUTH, sessionId);
            return;
        }

        Object principal = ((UsernamePasswordAuthenticationToken) event.getUser()).getPrincipal();

        if (principal == null) {
            log.warn(LogConstants.WS_DISCONNECT_NO_AUTH, sessionId);
            return;
        }

        String username = principal.toString();

        log.info(LogConstants.WS_DISCONNECT_DETECTED, username, sessionId);

        try {
            // A disconnecting user must leave every queue, not just the Santase one.
            santaseService.cancelSearchGame(username);
            tablaService.cancelSearch(username);
        } catch (Exception ex) {
            log.error(LogConstants.GAME_SEARCH_CANCEL_ERROR, username, ex);
        }
    }
}
