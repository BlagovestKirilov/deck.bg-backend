package bg.deck.santase.service;

import bg.deck.common.service.ChallengeStarter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Starts a сантасе game between two friends, from an accepted challenge. */
@RequiredArgsConstructor
@Component
public class SantaseChallengeStarter implements ChallengeStarter {

    private final SantaseService santaseService;

    @Override
    public String code() {
        return SantaseService.SANTASE;
    }

    @Override
    public UUID startFriendly(String challenger, String challenged) {
        return santaseService.startFriendly(challenger, challenged);
    }
}
