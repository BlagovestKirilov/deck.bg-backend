package bg.deck.common.model.response;

import java.util.List;

/**
 * The games this player may see, already resolved.
 *
 * <p>No states and no scopes: what a player is not offered is simply absent,
 * and nothing here says whether it exists at all.
 *
 * @param services the codes, alphabetically
 */
public record AvailableServicesResponse(List<String> services) {

    public AvailableServicesResponse {
        services = List.copyOf(services);
    }
}
