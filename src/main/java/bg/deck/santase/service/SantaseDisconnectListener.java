package bg.deck.santase.service;

import bg.deck.common.constant.LogConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * A player whose socket closes leaves the сантасе queue: nobody is matched
 * with somebody who has already gone.
 *
 * <p>Santase's own listener, as belot and табла have theirs, rather than a
 * line in a shared one — which would have to know every game there is.
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class SantaseDisconnectListener {

    private final SantaseService santaseService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null || user.getName() == null) {
            return;
        }
        try {
            santaseService.cancelSearchGame(user.getName());
        } catch (Exception ex) {
            log.error(LogConstants.GAME_SEARCH_CANCEL_ERROR, user.getName(), ex);
        }
    }
}
