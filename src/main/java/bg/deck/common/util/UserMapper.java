package bg.deck.common.util;

import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.User;
import bg.deck.common.model.request.RegisterRequest;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.time.Instant;

import static bg.deck.common.enums.Role.ROLE_USER;

@Mapper(componentModel = "spring", imports = Instant.class)
public interface UserMapper {
    User toEntity(RegisterRequest registerRequest);

    @AfterMapping
    default void setDefaults(@MappingTarget User user) {
        user.setRole(ROLE_USER);
        user.setIsEmailConfirmed(false);
        // The stats rows carry rank and rating now, and are created per game
        // when the account is registered.
    }

    /**
     * The record left behind by a deleted account: the username, so old games
     * still name the player, and the date. Nothing personal can come across —
     * {@link DeletedUser} has nowhere to put it.
     */
    DeletedUser toDeletedUser(User user);

    @AfterMapping
    default void setDeletedAt(@MappingTarget DeletedUser deletedUser) {
        deletedUser.setDeletedAt(Instant.now());
    }
}
