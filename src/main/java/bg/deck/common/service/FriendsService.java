package bg.deck.common.service;

import bg.deck.common.model.dto.FriendLinks;
import bg.deck.common.model.dto.GameStatsDTO;
import bg.deck.common.model.response.FriendView;
import bg.deck.common.model.response.FriendsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's friends page, put together: who their friends are, who is here
 * and who is playing, each friend's rank at every game, and the requests and
 * challenge waiting either way.
 */
@RequiredArgsConstructor
@Service
public class FriendsService {

    private final FriendshipService friendshipService;
    private final ChallengeService challengeService;
    private final PresenceService presenceService;
    private final List<GameRecordProvider> records;

    public FriendsResponse pageOf(String me) {
        FriendLinks links = friendshipService.linksOf(me);
        List<FriendView> friends = links.friends().stream()
                .map(this::viewOf)
                // Who can be challenged now first: here and free, then
                // playing, then away — each by name.
                .sorted(Comparator.comparingInt(FriendsService::availability)
                        .thenComparing(FriendView::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new FriendsResponse(friends, links.incoming(), links.outgoing(),
                challengeService.incomingFor(me).orElse(null),
                challengeService.outgoingFor(me).orElse(null));
    }

    private FriendView viewOf(String username) {
        Map<String, GameStatsDTO> ranks = new LinkedHashMap<>();
        records.forEach(game -> ranks.put(game.code(), game.recordOf(username)));
        return new FriendView(username, presenceService.isOnline(username),
                presenceService.playing(username).orElse(null), ranks);
    }

    private static int availability(FriendView friend) {
        if (friend.online() && friend.playing() == null) {
            return 0;
        }
        return friend.playing() != null ? 1 : 2;
    }
}
