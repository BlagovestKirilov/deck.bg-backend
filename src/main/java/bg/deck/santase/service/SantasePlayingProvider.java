package bg.deck.santase.service;

import bg.deck.common.service.PlayingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether a player is busy with сантасе now, for the friends list: in a game,
 * or searching for one — their screen is the game's, which hears no challenge.
 */
@RequiredArgsConstructor
@Component
public class SantasePlayingProvider implements PlayingProvider {

    private final SantaseTableService santaseTableService;
    private final SantaseService santaseService;

    @Override
    public String code() {
        return SantaseService.SANTASE;
    }

    @Override
    public boolean isPlaying(String username) {
        return santaseService.isSearching(username) || santaseTableService.activeGameId(username).isPresent();
    }
}
