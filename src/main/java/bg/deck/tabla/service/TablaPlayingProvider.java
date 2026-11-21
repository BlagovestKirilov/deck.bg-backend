package bg.deck.tabla.service;

import bg.deck.common.service.PlayingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether a player is busy with табла now, for the friends list: in a game,
 * or searching for one — their screen is the game's, which hears no challenge.
 */
@RequiredArgsConstructor
@Component
public class TablaPlayingProvider implements PlayingProvider {

    private final TablaUtilService tablaUtilService;
    private final TablaService tablaService;

    @Override
    public String code() {
        return TablaService.TABLA;
    }

    @Override
    public boolean isPlaying(String username) {
        return tablaService.isSearching(username) || tablaUtilService.activeGameId(username).isPresent();
    }
}
