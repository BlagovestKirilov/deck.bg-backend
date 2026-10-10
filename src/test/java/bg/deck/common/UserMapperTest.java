package bg.deck.common;

import bg.deck.common.enums.Role;
import bg.deck.common.model.DeletedUser;
import bg.deck.common.model.User;
import bg.deck.common.model.base.BaseEntity;
import bg.deck.common.util.UserMapper;
import bg.deck.common.util.UserMapperImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMapperTest {

    private final UserMapper mapper = new UserMapperImpl();

    /**
     * The record a deleted account leaves behind keeps the username — old games
     * still name the player — and the date. Nothing else.
     *
     * <p>The second half of this test is the part that matters: DeletedUser must
     * not grow a field that can hold personal data again. It used to inherit the
     * user's shape, and so kept the email, password hash and IP address of every
     * deleted account for good.
     */
    @Test
    void deletedRecordKeepsOnlyTheUsername() {
        User user = new User();
        user.setUsername("ninja2011");
        user.setEmail("ninja@example.com");
        user.setPassword("$2a$10$hash");
        user.setIpAddress("203.0.113.7");
        user.setRole(Role.ROLE_USER);
        user.setIsEmailConfirmed(true);

        DeletedUser deleted = mapper.toDeletedUser(user);

        assertEquals("ninja2011", deleted.getUsername());
        assertNotNull(deleted.getDeletedAt());

        Set<String> fields = Arrays.stream(DeletedUser.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("username", "deletedAt"), fields,
                "a deleted account is a name and a date — nothing else belongs here");
        assertEquals(BaseEntity.class, DeletedUser.class.getSuperclass(),
                "extending the user's base class is what made deletion copy whole accounts");
    }
}
