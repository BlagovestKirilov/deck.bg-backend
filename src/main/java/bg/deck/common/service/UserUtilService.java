package bg.deck.common.service;

import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.User;
import bg.deck.common.repository.DeletedUserRepository;
import bg.deck.common.util.UserMapper;
import bg.deck.common.model.event.UserDeleted;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
@Service
public class UserUtilService {
    private final DeletedUserRepository deletedUserRepository;
    private final EmailConfirmationService emailConfirmationService;
    private final ForgotPasswordService forgotPasswordService;
    private final UserDeletionService userDeletionService;
    private final UserAccountService userAccountService;
    private final FriendshipService friendshipService;
    private final ApplicationEventPublisher events;
    private final UserMapper userMapper;

    /**
     * Ends an account, leaving a tombstone in its place.
     *
     * <p>Everything in public the user owned is handed to that tombstone before
     * the row itself goes, and each of those tables belongs to a service of its
     * own, which knows how to let go of a user without losing the row. The
     * games keep their seats in schemas of their own and let go of the name
     * when they hear {@link UserDeleted}.
     */
    public void deleteUser(User user) {
        DeletedUser deletedUser = userMapper.toDeletedUser(user);
        deletedUserRepository.save(deletedUser);

        emailConfirmationService.reassignToDeletedUser(user, deletedUser);
        forgotPasswordService.reassignToDeletedUser(user, deletedUser);
        userDeletionService.reassignToDeletedUser(user, deletedUser);

        String username = user.getUsername();
        // Read before the delete: the friendships go with the account.
        List<String> linked = friendshipService.everyoneLinkedTo(username);
        userAccountService.delete(user);
        friendshipService.tellGone(username, linked);

        // Anything with a schema of its own clears itself. Calling a game
        // from here would put a public-schema class in charge of that game's
        // tables, which is the seam the whole arrangement depends on.
        events.publishEvent(new UserDeleted(username));
    }
}
