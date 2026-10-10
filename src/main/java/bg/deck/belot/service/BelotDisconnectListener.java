package bg.deck.belot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * A player whose socket closes while their table is still waiting for its
 * fourth gets up from it, as a santase or табла search is cancelled.
 *
 * <p>Otherwise a closed browser kept the seat, the table filled, and the game
 * started without them — to be played for them three times and then lost,
 * at twice the rating. A table already being played is not touched: a
 * dropped connection there is a reconnect, not a departure.
 *
 * <p>Belot's own listener rather than a line in the shared one, which may not
 * know belot exists.
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class BelotDisconnectListener {

    private final BelotService belotService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null || user.getName() == null) {
            return;
        }
        try {
            belotService.leave(user.getName());
        } catch (Exception ex) {
            log.error("Belot: could not get {} up from a waiting table on disconnect", user.getName(), ex);
        }
    }
}
