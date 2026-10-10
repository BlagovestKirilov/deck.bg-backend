package bg.deck.tabla.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.service.GameRecordProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A player's табла record, for the profile. Listed after santase. */
@Order(2)
@RequiredArgsConstructor
@Component
public class TablaRecordProvider implements GameRecordProvider {

    private final TablaStatsService tablaStatsService;

    @Override
    public String code() {
        return GameType.TABLA.name();
    }

    @Override
    public GameStatsDTO recordOf(String username) {
        return tablaStatsService.view(username);
    }
}
