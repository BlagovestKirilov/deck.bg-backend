package bg.deck.common;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public holds the accounts and nothing any game owns.
 *
 * <p>Every game keeps its tables in a schema of its own — belot, santase,
 * tabla — so a game's table appearing in public is the first step back to
 * the shared tables they were moved out of.
 */
@DisplayName("Public holds only accounts")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:publicschema;INIT=CREATE SCHEMA IF NOT EXISTS belot\\\\;CREATE SCHEMA IF NOT EXISTS santase\\\\;CREATE SCHEMA IF NOT EXISTS tabla",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
class PublicSchemaTest {

    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("the application maps exactly the account tables into public")
    void publicHoldsTheAccounts() {
        List<?> tables = entityManager.createNativeQuery("""
                        select lower(table_name) from information_schema.tables
                         where upper(table_schema) = 'PUBLIC'
                         order by 1
                        """)
                .getResultList();

        assertThat(tables).isEqualTo(List.of("available_service", "deleted_users", "email_confirmation",
                "forgot_password", "user_deletion", "users"));
    }
}
