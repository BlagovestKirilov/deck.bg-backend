package bg.deck.belot.service;

import bg.deck.common.service.PlayingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Whether a player is at a belot table now — playing or waiting for it to fill — for the friends list. */
@RequiredArgsConstructor
@Component
public class BelotPlayingProvider implements PlayingProvider {

    private final BelotTableService belotTableService;

    @Override
    public String code() {
        return BelotService.BELOT;
    }

    @Override
    public boolean isPlaying(String username) {
        return belotTableService.tableOf(username).isPresent();
    }
}
