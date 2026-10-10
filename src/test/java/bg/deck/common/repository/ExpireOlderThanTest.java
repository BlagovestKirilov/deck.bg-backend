package bg.deck.common.repository;

import bg.deck.common.enums.EmailConfirmationStatus;
import bg.deck.common.enums.ForgotPasswordStatus;
import bg.deck.common.enums.Role;
import bg.deck.common.enums.UserDeletionStatus;
import bg.deck.common.model.EmailConfirmation;
import bg.deck.common.model.ForgotPassword;
import bg.deck.common.model.User;
import bg.deck.common.model.UserDeletion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static bg.deck.common.constant.Constants.EMAIL_CONFIRMATION_VALIDITY;
import static bg.deck.common.constant.Constants.LINK_VALIDITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scheduler's queries against a real database rather than a mock of one.
 *
 * <p>A mocked repository proves nothing about a query: the JPQL is parsed when
 * the application starts, and a bulk update that never compiles would first be
 * heard about in production. Here it runs.
 */
@DisplayName("Retiring the links nobody opened")
@DataJpaTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:links;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class ExpireOlderThanTest {

    @Autowired private ForgotPasswordRepository forgotPasswordRepository;
    @Autowired private EmailConfirmationRepository emailConfirmationRepository;
    @Autowired private UserDeletionRepository userDeletionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("a reset past its quarter of an hour is retired; a newer one is left alone")
    void retiresOldResets() {
        User user = persistedUser();
        ForgotPassword stale = forgotPasswordRepository.save(new ForgotPassword(user));
        ForgotPassword fresh = forgotPasswordRepository.save(new ForgotPassword(user));
        entityManager.flush();
        backdate("forgot_password", stale, LINK_VALIDITY.plusMinutes(5));

        int retired = forgotPasswordRepository.expireOlderThan(
                Instant.now().minus(LINK_VALIDITY),
                ForgotPasswordStatus.PENDING,
                ForgotPasswordStatus.EXPIRED);

        assertEquals(1, retired, "only the one that ran out");
        entityManager.clear();
        assertEquals(ForgotPasswordStatus.EXPIRED, reload(ForgotPassword.class, stale).getStatus());
        assertEquals(ForgotPasswordStatus.PENDING, reload(ForgotPassword.class, fresh).getStatus());
    }

    @Test
    @DisplayName("a confirmation is retired on its own, longer clock")
    void retiresOldConfirmations() {
        User user = persistedUser();
        EmailConfirmation stale = emailConfirmationRepository.save(new EmailConfirmation(user));
        EmailConfirmation fresh = emailConfirmationRepository.save(new EmailConfirmation(user));
        entityManager.flush();
        // Older than a reset's fifteen minutes, younger than a confirmation's day.
        backdate("email_confirmation", fresh, LINK_VALIDITY.plusMinutes(5));
        backdate("email_confirmation", stale, EMAIL_CONFIRMATION_VALIDITY.plusMinutes(5));

        int retired = emailConfirmationRepository.expireOlderThan(
                Instant.now().minus(EMAIL_CONFIRMATION_VALIDITY),
                EmailConfirmationStatus.PENDING,
                EmailConfirmationStatus.EXPIRED);

        assertEquals(1, retired, "the one from yesterday, not the one from this hour");
        entityManager.clear();
        assertEquals(EmailConfirmationStatus.EXPIRED, reload(EmailConfirmation.class, stale).getStatus());
        assertEquals(EmailConfirmationStatus.PENDING, reload(EmailConfirmation.class, fresh).getStatus());
    }

    @Test
    @DisplayName("a deletion nobody confirmed is retired too")
    void retiresOldDeletions() {
        User user = persistedUser();
        UserDeletion stale = userDeletionRepository.save(new UserDeletion(user));
        entityManager.flush();
        backdate("user_deletion", stale, LINK_VALIDITY.plusMinutes(5));

        int retired = userDeletionRepository.expireOlderThan(
                Instant.now().minus(LINK_VALIDITY),
                UserDeletionStatus.PENDING,
                UserDeletionStatus.EXPIRED);

        assertEquals(1, retired);
        entityManager.clear();
        assertEquals(UserDeletionStatus.EXPIRED, reload(UserDeletion.class, stale).getStatus());
    }

    @Test
    @DisplayName("a link already used is not touched")
    void leavesSettledRowsAlone() {
        User user = persistedUser();
        ForgotPassword used = new ForgotPassword(user);
        used.setStatus(ForgotPasswordStatus.SUCCESS);
        forgotPasswordRepository.save(used);
        entityManager.flush();
        backdate("forgot_password", used, LINK_VALIDITY.plusHours(3));

        int retired = forgotPasswordRepository.expireOlderThan(
                Instant.now().minus(LINK_VALIDITY),
                ForgotPasswordStatus.PENDING,
                ForgotPasswordStatus.EXPIRED);

        assertEquals(0, retired, "a password that was changed stays changed");
        entityManager.clear();
        assertEquals(ForgotPasswordStatus.SUCCESS, reload(ForgotPassword.class, used).getStatus());
    }

    /* ---------------- helpers ---------------- */

    private User persistedUser() {
        User user = new User();
        user.setUsername("petko91-" + System.nanoTime());
        user.setEmail(System.nanoTime() + "@example.com");
        user.setPassword("hashed");
        user.setRole(Role.ROLE_USER);
        user.setIsEmailConfirmed(true);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    /**
     * Moves a row's creation date back. {@code createdAt} is written by
     * Hibernate on insert and never updated, so this goes round it in SQL —
     * the only way to have a row that is genuinely old inside one test.
     */
    private void backdate(String table, Object entity, java.time.Duration age) {
        Object id = entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil().getIdentifier(entity);
        entityManager.createNativeQuery(
                        "update " + table + " set created_at = :moment where id = :id")
                .setParameter("moment", Instant.now().minus(age))
                .setParameter("id", id)
                .executeUpdate();
    }

    private <T> T reload(Class<T> type, Object entity) {
        Object id = entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil().getIdentifier(entity);
        return entityManager.find(type, id);
    }
}
