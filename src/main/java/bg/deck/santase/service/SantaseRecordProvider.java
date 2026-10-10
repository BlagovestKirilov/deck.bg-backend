package bg.deck.santase.service;

import bg.deck.common.enums.GameType;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.service.GameRecordProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A player's сантасе record, for the profile. Listed first. */
@Order(1)
@RequiredArgsConstructor
@Component
public class SantaseRecordProvider implements GameRecordProvider {

    private final SantaseStatsService santaseStatsService;

    @Override
    public String code() {
        return GameType.SANTASE.name();
    }

    @Override
    public GameStatsDTO recordOf(String username) {
        return santaseStatsService.view(username);
    }
}
