package bg.deck.tabla.service;

import bg.deck.common.service.ChallengeStarter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Starts a табла game between two friends, from an accepted challenge. */
@RequiredArgsConstructor
@Component
public class TablaChallengeStarter implements ChallengeStarter {

    private final TablaService tablaService;

    @Override
    public String code() {
        return TablaService.TABLA;
    }

    @Override
    public UUID startFriendly(String challenger, String challenged) {
        return tablaService.startFriendly(challenger, challenged);
    }
}
